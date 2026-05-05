package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.TagMapper;
import com.jacolp.pojo.dto.tag.TagNoteCountDTO;
import com.jacolp.pojo.dto.tag.TagAddDTO;
import com.jacolp.pojo.dto.tag.TagBatchAddDTO;
import com.jacolp.pojo.dto.tag.TagModifyDTO;
import com.jacolp.pojo.dto.tag.TagQueryDTO;
import com.jacolp.pojo.dto.tag.UserTagAddDTO;
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
import com.jacolp.service.AuditService;
import com.jacolp.service.NoteRelationService;
import com.jacolp.service.NoteServiceOld;
import com.jacolp.service.TagService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    @Autowired private TagMapper tagMapper;

    // 来自其他模块的 Mapper
    @Autowired private AuditService auditService;
    @Autowired private NoteServiceOld noteServiceOld;
    @Autowired private NoteRelationService noteRelationService;

    @Override
    public void addTag(TagAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String tagName = normalizeTagName(dto.getTagName());
        validateTagName(tagName);

        TagEntity existed = tagMapper.selectByUserIdAndTagName(userId, tagName);
        if (existed != null) {
            throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
        }

        TagEntity tag = new TagEntity();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        tag.setIsPass(AuditConstant.PASS);
        int count = tagMapper.insertTag(tag);
        if (count <= 0) {
            throw new BaseException(TagConstant.TAG_ADD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagBatchAddVO batchAddTags(TagBatchAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (dto == null || dto.getTagNames() == null || dto.getTagNames().isEmpty()) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }

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
                tag.setIsPass(AuditConstant.PASS);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的标签 ID 列表不能为空");
        }

        Long userId = BaseContext.getCurrentId();
        List<TagNoteCountDTO> checks = tagMapper.selectDeleteChecksByIds(userId, ids);
        if (checks.size() != ids.size()) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

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

        Integer pageNumParam = dto.getPageNum();
        Integer pageSizeParam = dto.getPageSize();
        int pageNum = pageNumParam == null || pageNumParam <= 0 ? 1 : pageNumParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? 10 : pageSizeParam;
        PageHelper.startPage(pageNum, pageSize);

        Long userId = dto.getUserId();
        if (userId != null && userId <= 0) {
            throw new BaseException(UserConstant.NOT_FIND_USER);
        }
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

        int pageNum = dto.getPageNum() == null || dto.getPageNum() <= 0 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

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
    public void addUserTag(UserTagAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String tagName = normalizeTagName(dto.getTagName());

        if (!StringUtils.hasText(tagName)) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }
        if (tagName.length() > TagConstant.MAX_TAG_NAME_LENGTH) {
            throw new BaseException(TagConstant.TAG_NAME_TOO_LONG);
        }

        TagEntity existed = tagMapper.selectByUserIdAndTagName(userId, tagName);
        if (existed != null) {
            throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
        }

        TagEntity tag = new TagEntity();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        tag.setIsPass(TagConstant.IS_NOT_PASS);

        int count = tagMapper.insertTag(tag);
        if (count <= 0) {
            throw new BaseException("创建标签失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserTag(Long id) {
        Long userId = BaseContext.getCurrentId();

        if (id == null || id <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }

        TagEntity tag = tagMapper.selectByIdAndUserId(id, userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        List<NoteTagMappingEntity> mappings = noteRelationService.listTagMappingsByNoteId(id);
        if (mappings != null && !mappings.isEmpty()) {
            throw new BaseException("该标签正在被使用，无法删除");
        }

        List<Long> ids = new ArrayList<>();
        ids.add(id);
        int count = tagMapper.deleteByIds(userId, ids);
        if (count <= 0) {
            throw new BaseException("删除标签失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserTag(UserTagAssignDTO dto) {
        Long userId = BaseContext.getCurrentId();

        if (dto.getTagId() == null || dto.getTagId() <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }
        if (dto.getTargetId() == null || dto.getTargetId() <= 0) {
            throw new BaseException("目标资源ID无效");
        }

        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        assignTagToNote(tag, dto.getTargetId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserTag(UserTagRemoveDTO dto) {
        Long userId = BaseContext.getCurrentId();

        if (dto.getTagId() == null || dto.getTagId() <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }
        if (dto.getTargetId() == null || dto.getTargetId() <= 0) {
            throw new BaseException("目标资源ID无效");
        }

        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        removeTagFromNote(tag, dto.getTargetId(), userId);
    }

    private void assignTagToNote(TagEntity tag, Long noteId, Long userId) {
        NoteEntity note = noteServiceOld.getNoteEntityById(noteId);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能绑定到自己的笔记");
        }

        List<NoteTagMappingEntity> existingMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        for (NoteTagMappingEntity mapping : existingMappings) {
            if (mapping.getTagId() != null
                    && mapping.getTagId().equals(tag.getId())
                    && NoteConstant.NOT_DELETED.equals(mapping.getIsDeleted())) {
                throw new BaseException("该标签已绑定到此笔记");
            }
        }

        NoteTagMappingEntity mapping = new NoteTagMappingEntity();
        mapping.setNoteId(noteId);
        BeanUtils.copyProperties(tag, mapping);
        mapping.setIsDeleted(NoteConstant.NOT_DELETED);

        int count = noteRelationService.batchInsertTagMappings(List.of(mapping));
        if (count <= 0) {
            throw new BaseException("绑定标签失败");
        }
    }

    private void removeTagFromNote(TagEntity tag, Long noteId, Long userId) {
        NoteEntity note = noteServiceOld.getNoteEntityById(noteId);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能操作自己的笔记");
        }

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

        if (targetMapping == null) {
            throw new BaseException("该标签未绑定到此笔记");
        }

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