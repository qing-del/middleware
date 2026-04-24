package com.jacolp.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.TagConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.MetaAuditMapper;
import com.jacolp.mapper.TagMapper;
import com.jacolp.pojo.domain.TagNoteCountDO;
import com.jacolp.pojo.dto.TagAddDTO;
import com.jacolp.pojo.dto.TagBatchAddDTO;
import com.jacolp.pojo.dto.TagModifyDTO;
import com.jacolp.pojo.dto.TagQueryDTO;
import com.jacolp.pojo.dto.UserTagQueryDTO;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.TagBatchAddVO;
import com.jacolp.pojo.vo.TagVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private MetaAuditMapper metaAuditMapper;

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
        List<TagNoteCountDO> checks = tagMapper.selectDeleteChecksByIds(userId, ids);
        if (checks.size() != ids.size()) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        for (TagNoteCountDO check : checks) {
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
        int pendingCount = metaAuditMapper.countPendingAuditByApplyTypeAndTargetId(AuditConstant.TAG_APPLY_TYPE, tagId);
        if (pendingCount > 0) {
            throw new BaseException("该标签已有待审核的申请");
        }

        MetaAuditRecordEntity record = new MetaAuditRecordEntity();
        record.setApplicantUserId(userId);
        record.setApplyType(AuditConstant.TAG_APPLY_TYPE);
        record.setTargetId(tagId);
        metaAuditMapper.insertAuditRecord(record);
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