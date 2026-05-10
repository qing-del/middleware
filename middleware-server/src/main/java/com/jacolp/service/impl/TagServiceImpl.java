package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.jacolp.context.PermissionContext;
import com.jacolp.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.TagConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.TagMapper;
import com.jacolp.pojo.dto.tag.TagNoteCountDTO;
import com.jacolp.pojo.dto.tag.TagAddDTO;
import com.jacolp.pojo.dto.tag.TagBatchAddDTO;
import com.jacolp.pojo.dto.tag.TagModifyDTO;
import com.jacolp.pojo.dto.tag.TagQueryDTO;
import com.jacolp.pojo.dto.tag.UserTagAssignDTO;
import com.jacolp.pojo.dto.tag.UserTagQueryDTO;
import com.jacolp.pojo.dto.tag.UserTagRemoveDTO;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.tag.TagBatchAddVO;
import com.jacolp.pojo.vo.tag.TagStatsVO;
import com.jacolp.pojo.vo.tag.TagVO;
import com.jacolp.pojo.vo.tag.UserTagSimpleVO;
import com.jacolp.result.PageResult;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    @Autowired private TagMapper tagMapper;

    // 来自其他模块的 Mapper
    @Autowired private AuditService auditService;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteRelationService noteRelationService;

    @Override
    public void addTag(TagAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String tagName = normalizeTagName(dto.getTagName());
        validateTagName(tagName);

        // 检查是否存在同主题下的同名标签
        TagEntity existed = tagMapper.selectByUserIdAndTagName(userId, tagName);
        if (existed != null) {
            throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
        }

        TagEntity tag = new TagEntity();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        tag.setIsPass(AuditConstant.WAIT);  // 默认处于待审核状态
        int count = tagMapper.insertTag(tag);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_ADD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagBatchAddVO batchAddTags(TagBatchAddDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 去重并保序，同时过滤空白
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tagName : dto.getTagNames()) {
            if (!StringUtils.hasText(tagName)) {
                continue;
            }
            String trimmed = normalizeTagName(tagName);
            validateTagName(trimmed);
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }

        List<String> existedNames = tagMapper.selectTagNamesByUserId(userId);
        Set<String> existedSet = new HashSet<>(existedNames);

        List<String> existingTags = new ArrayList<>();
        List<TagEntity> toInsert = new ArrayList<>();
        for (String tagName : normalized) {
            if (existedSet.contains(tagName)) {
                existingTags.add(tagName);
            } else {
                TagEntity tag = new TagEntity();
                tag.setUserId(userId);
                tag.setTagName(tagName);
                tag.setIsPass(AuditConstant.WAIT);  // 默认处于待审核状态
                toInsert.add(tag);
            }
        }

        int successCount = 0;
        if (!toInsert.isEmpty()) {
            successCount = tagMapper.batchInsertTags(toInsert);
        }
        return new TagBatchAddVO(successCount, existingTags);
    }

    @Override
    public void modifyTag(TagModifyDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateTagId(dto.getId());

        TagEntity existed = tagMapper.selectByIdAndUserId(dto.getId(), userId);
        if (existed == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 检查标签是否被引用，被引用的状态下无法修改
        if (noteRelationService.countRelationByTagId(dto.getId()) > 0) {
            throw new BaseException(TagConstant.TAG_REFERENCED);
        }

        // 检查是否存在重名的标签
        String tagName = normalizeTagName(dto.getTagName());
        validateTagName(tagName);
        if (!tagName.equals(existed.getTagName())) {
            TagEntity duplicate = tagMapper.selectByUserIdAndTagName(userId, tagName);
            if (duplicate != null && !duplicate.getId().equals(dto.getId())) {
                throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
            }
        }

        TagEntity update = new TagEntity();
        update.setId(dto.getId());
        update.setUserId(userId);
        update.setTagName(tagName);
        int count = tagMapper.updateTag(update);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_UPDATE_FAILED);
        }
    }

    /**
     * 删除标签
     * <p>- 查询待删除的标签列表时，会根据 {@link PermissionContext#isAdmin()} 来判断是否需要开启用户过滤</p>
     * @param ids
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTags(List<Long> ids) {
        // 获取删除列表
        Long userId = PermissionContext.isAdmin() ? null : BaseContext.getCurrentId();
        List<TagNoteCountDTO> checks = tagMapper.selectDeleteChecksByIds(userId, ids);
        if (checks.size() != ids.size()) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 检查是否存在引用
        for (TagNoteCountDTO check : checks) {
            if (check.getNoteCount() != null && check.getNoteCount() > 0) {
                throw new BaseException(TagConstant.TAG_DELETE_NOT_ALLOWED_PREFIX
                        + check.getTagName()
                        + TagConstant.TAG_DELETE_NOT_ALLOWED_SUFFIX);
            }
        }

        int count = tagMapper.deleteByIds(userId, ids);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_DELETE_FAILED);
        }
    }

    @Override
    public PageResult listTags(TagQueryDTO dto) {
        if (dto == null) {
            dto = new TagQueryDTO();
        }

        Long userId = dto.getUserId();
        if (userId != null && userId <= 0) {
            throw new BaseException(UserConstant.NOT_FIND_USER);
        }

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TagVO> records = tagMapper.listByCondition(userId, normalizeKeyword(dto.getKeyword()));
        PageInfo<TagVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端条件查询：当前用户自己的标签 + 别人已通过审核的标签。
     */
    @Override
    public PageResult listUserTags(UserTagQueryDTO dto) {
        if (dto == null) {
            dto = new UserTagQueryDTO();
        }
        Long userId = BaseContext.getCurrentId();

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());

        List<TagVO> records = tagMapper.listByUserCondition(userId, normalizeKeyword(dto.getKeyword()));
        PageInfo<TagVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端发起标签审核申请。
     */
    @Override
    public void submitTagAudit(Long tagId) {
        Long userId = BaseContext.getCurrentId();
        validateTagId(tagId);

        TagEntity tag = tagMapper.selectByIdAndUserId(tagId, userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }
        if (AuditConstant.PASS.equals(tag.getIsPass())) {
            throw new BaseException("该标签已通过审核");
        }
        if (auditService.hasPendingMetaAudit(AuditConstant.TAG_APPLY_TYPE, tagId)) {
            throw new BaseException("该标签已有待审核的申请");
        }

        MetaAuditRecordEntity record = new MetaAuditRecordEntity();
        record.setApplicantUserId(userId);
        record.setApplyType(AuditConstant.TAG_APPLY_TYPE);
        record.setTargetId(tagId);
        auditService.createMetaAuditRecord(record);
    }

    /**
     * 获取当前用户标签统计。
     */
    @Override
    public TagStatsVO getUserTagStats() {
        Long userId = BaseContext.getCurrentId();
        long tagCount = tagMapper.countByUserId(userId);
        long passedCount = tagMapper.countPassedByUserId(userId);
        return new TagStatsVO(tagCount, passedCount);
    }

    @Override
    public TagEntity getByIdAndUserId(Long id, Long userId) {
        return tagMapper.selectByIdAndUserId(id, userId);
    }

    @Override
    public List<TagEntity> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectByIds(ids);
    }

    @Override
    public List<TagEntity> getByNamesAndUserId(List<String> names, Long userId) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectIdsByNamesAndUserId(names, userId);
    }

    // ===== 用户端方法 =====

    @Override
    public List<UserTagSimpleVO> listUserTagSimples() {
        Long userId = BaseContext.getCurrentId();
        List<TagEntity> tags = tagMapper.selectByUserId(userId);
        return tags.stream()
                .map(tag -> {
                    UserTagSimpleVO vo = new UserTagSimpleVO();
                    vo.setId(tag.getId());
                    vo.setTagName(tag.getTagName());
                    vo.setCreateTime(tag.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void assignUserTag(UserTagAssignDTO dto) {
        Long userId = BaseContext.getCurrentId();

        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        assignTagToNote(tag, dto.getTargetId(), userId);
    }

    @Override
    public void removeUserTag(UserTagRemoveDTO dto) {
        Long userId = BaseContext.getCurrentId();

        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        removeTagFromNote(tag, dto.getTargetId(), userId);
    }

    /**
     * 分配标签给目标笔记
     * <p>- 先使用 {@link NoteCoreService#getById(Long)} 来获取笔记（自带身份校验）</p>
     * <p>- 使用{@link NoteRelationService#listTagMappingsByNoteId(Long)} 获取列表来校验是否存在重复绑定</p>
     * <p>- 若不存在重复，使用{@link NoteRelationService#batchInsertTagMappings(List)} 创建映射关系</p>
     * @throws BaseException 不存在笔记 | 已有关联映射 | 创建映射关系失败
     */
    private void assignTagToNote(TagEntity tag, Long noteId, Long userId) {
        NoteEntity note = noteCoreService.getById(noteId);
        // 显示校验所属权（实际上已经校验过了）
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能绑定到自己的笔记");
        }

        // 检查是否存在重复绑定
        List<NoteTagMappingEntity> existingMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        for (NoteTagMappingEntity mapping : existingMappings) {
            if (mapping.getTagId() != null
                    && mapping.getTagId().equals(tag.getId())
                    && NoteConstant.NOT_DELETED.equals(mapping.getIsDeleted())) {
                throw new BaseException("该标签已绑定到此笔记");
            }
        }

        // 创建映射
        NoteTagMappingEntity mapping = new NoteTagMappingEntity();
        mapping.setNoteId(noteId);
        BeanUtils.copyProperties(tag, mapping);
        mapping.setIsDeleted(NoteConstant.NOT_DELETED);

        int count = noteRelationService.batchInsertTagMappings(List.of(mapping));
        if (count <= 0) {
            throw new BaseException("绑定标签失败");
        }
    }

    /**
     * 移除标签从目标笔记
     * <p>- 先使用 {@link NoteCoreService#getById(Long)} 来获取笔记（自带身份校验）</p>
     * <p>- 使用{@link NoteRelationService#listTagMappingsByNoteId(Long)} 获取列表来校验是否存在映射关系</p>
     * <p>- 若存在映射关系，使用{@link NoteRelationService#unbindTagMappingById(Long)} 删除映射关系</p>
     * @throws BaseException 不存在笔记 | 未找到映射关系 | 删除映射关系失败
     */
    private void removeTagFromNote(TagEntity tag, Long noteId, Long userId) {
        NoteEntity note = noteCoreService.getById(noteId);
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能操作自己的笔记");
        }

        // 获取映射关系
        List<NoteTagMappingEntity> mappings = noteRelationService.listTagMappingsByNoteId(noteId);
        NoteTagMappingEntity targetMapping = null;
        for (NoteTagMappingEntity mapping : mappings) {
            if (mapping.getTagId() != null
                    && mapping.getTagId().equals(tag.getId())
                    && NoteConstant.NOT_DELETED.equals(mapping.getIsDeleted())) {
                targetMapping = mapping;
                break;
            }
        }

        // 检查是否找到对应的映射关系
        if (targetMapping == null) {
            throw new BaseException("该标签未绑定到此笔记");
        }

        // 删除映射关系
        int count = noteRelationService.unbindTagMappingById(targetMapping.getId());
        if (count <= 0) {
            throw new BaseException("解除绑定失败");
        }
    }

    @Override
    public List<TagNoteCountDTO> listDeleteChecksByIds(Long userId, List<Long> ids) {
        return tagMapper.selectDeleteChecksByIds(userId, ids);
    }

    @Override
    public int updatePassStatusByIds(List<Long> ids, Short isPass) {
        return tagMapper.updatePassByIds(ids, isPass);
    }

    private String normalizeTagName(String tagName) {
        if (tagName == null) {
            return null;
        }
        return tagName.trim();
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private void validateTagName(String tagName) {
        if (!StringUtils.hasText(tagName)) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }
        if (tagName.length() > TagConstant.MAX_TAG_NAME_LENGTH) {
            throw new BaseException(TagConstant.TAG_NAME_TOO_LONG);
        }
    }

    private void validateTagId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }
    }
}