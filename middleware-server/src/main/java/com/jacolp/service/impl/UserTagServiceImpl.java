package com.jacolp.service.impl;

import com.jacolp.constant.TagConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.mapper.TagMapper;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.pojo.dto.tag.UserTagAddDTO;
import com.jacolp.pojo.dto.tag.UserTagAssignDTO;
import com.jacolp.pojo.dto.tag.UserTagRemoveDTO;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.vo.tag.UserTagSimpleVO;
import com.jacolp.service.UserTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户端标签服务实现
 */
@Service
@Slf4j
public class UserTagServiceImpl implements UserTagService {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private NoteTagMappingMapper noteTagMappingMapper;

    @Override
    public List<UserTagSimpleVO> listTags() {
        // 获取当前用户ID
        Long userId = BaseContext.getCurrentId();

        // 查询当前用户的标签
        List<TagEntity> tags = tagMapper.selectByUserId(userId);

        // 转换为VO
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
    public void createTag(UserTagAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String tagName = normalizeTagName(dto.getTagName());

        // 校验标签名称
        if (!StringUtils.hasText(tagName)) {
            throw new BaseException(TagConstant.TAG_NAME_REQUIRED);
        }
        if (tagName.length() > TagConstant.MAX_TAG_NAME_LENGTH) {
            throw new BaseException(TagConstant.TAG_NAME_TOO_LONG);
        }

        // 检查是否已存在同名标签
        TagEntity existed = tagMapper.selectByUserIdAndTagName(userId, tagName);
        if (existed != null) {
            throw new BaseException(TagConstant.TAG_ALREADY_EXISTS);
        }

        // 创建标签
        TagEntity tag = new TagEntity();
        tag.setUserId(userId);
        tag.setTagName(tagName);
        tag.setIsPass((short) 1); // 默认通过审核

        int count = tagMapper.insertTag(tag);
        if (count <= 0) {
            throw new BaseException("创建标签失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        Long userId = BaseContext.getCurrentId();

        // 校验标签ID
        if (id == null || id <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }

        // 查询标签并校验归属
        TagEntity tag = tagMapper.selectByIdAndUserId(id, userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 检查是否有笔记使用该标签
        List<NoteTagMappingEntity> mappings = noteTagMappingMapper.selectByNoteId(id);
        if (mappings != null && !mappings.isEmpty()) {
            throw new BaseException("该标签正在被使用，无法删除");
        }

        // 软删除标签（如果实体有isDeleted字段，这里需要软删除逻辑）
        // 由于当前TagEntity没有isDeleted字段，暂时使用物理删除
        // 如果需要软删除，需要在TagEntity中添加isDeleted字段
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        int count = tagMapper.deleteByIds(userId, ids);
        if (count <= 0) {
            throw new BaseException("删除标签失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTag(UserTagAssignDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 校验标签ID
        if (dto.getTagId() == null || dto.getTagId() <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }

        // 校验目标资源ID
        if (dto.getTargetId() == null || dto.getTargetId() <= 0) {
            throw new BaseException("目标资源ID无效");
        }

        // 校验目标资源类型
        if (!StringUtils.hasText(dto.getTargetType()) ||
            (!"note".equals(dto.getTargetType()) && !"topic".equals(dto.getTargetType()))) {
            throw new BaseException("目标资源类型无效，只支持 note 或 topic");
        }

        // 校验标签归属
        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 根据目标资源类型处理绑定
        if ("note".equals(dto.getTargetType())) {
            // 绑定到笔记
            assignTagToNote(dto.getTagId(), dto.getTargetId(), userId);
        } else {
            // 绑定到主题
            assignTagToTopic(dto.getTagId(), dto.getTargetId(), userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTag(UserTagRemoveDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 校验标签ID
        if (dto.getTagId() == null || dto.getTagId() <= 0) {
            throw new BaseException(TagConstant.TAG_ID_INVALID);
        }

        // 校验目标资源ID
        if (dto.getTargetId() == null || dto.getTargetId() <= 0) {
            throw new BaseException("目标资源ID无效");
        }

        // 校验目标资源类型
        if (!StringUtils.hasText(dto.getTargetType()) ||
            (!"note".equals(dto.getTargetType()) && !"topic".equals(dto.getTargetType()))) {
            throw new BaseException("目标资源类型无效，只支持 note 或 topic");
        }

        // 校验标签归属
        TagEntity tag = tagMapper.selectByIdAndUserId(dto.getTagId(), userId);
        if (tag == null) {
            throw new BaseException(TagConstant.TAG_NOT_FOUND);
        }

        // 根据目标资源类型处理解除绑定
        if ("note".equals(dto.getTargetType())) {
            // 从笔记解除绑定
            removeTagFromNote(dto.getTagId(), dto.getTargetId(), userId);
        } else {
            // 从主题解除绑定
            removeTagFromTopic(dto.getTagId(), dto.getTargetId(), userId);
        }
    }

    /**
     * 将标签绑定到笔记
     * @param tagId 标签ID
     * @param noteId 笔记ID
     * @param userId 用户ID
     */
    private void assignTagToNote(Long tagId, Long noteId, Long userId) {
        // 校验笔记归属
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || note.getIsDeleted() == 1) {
            throw new BaseException("笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能绑定到自己的笔记");
        }

        // 检查是否已绑定
        List<NoteTagMappingEntity> existingMappings = noteTagMappingMapper.selectByNoteId(noteId);
        for (NoteTagMappingEntity mapping : existingMappings) {
            if (mapping.getTagId() != null && mapping.getTagId().equals(tagId) &&
                mapping.getIsDeleted() == 0) {
                throw new BaseException("该标签已绑定到此笔记");
            }
        }

        // 创建绑定关系
        NoteTagMappingEntity mapping = new NoteTagMappingEntity();
        mapping.setNoteId(noteId);
        mapping.setTagId(tagId);
        mapping.setParsedTagName("");
        mapping.setIsPass((short) 1);
        mapping.setIsDeleted((short) 0);

        int count = noteTagMappingMapper.batchInsertMappings(List.of(mapping));
        if (count <= 0) {
            throw new BaseException("绑定标签失败");
        }
    }

    /**
     * 将标签绑定到主题
     * @param tagId 标签ID
     * @param topicId 主题ID
     * @param userId 用户ID
     */
    private void assignTagToTopic(Long tagId, Long topicId, Long userId) {
        // 校验主题归属
        TopicEntity topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BaseException("主题不存在");
        }
        if (!topic.getUserId().equals(userId)) {
            throw new BaseException("只能绑定到自己的主题");
        }

        // 注意：当前设计可能没有topic-tag映射表
        // 如果需要topic-tag绑定，可能需要创建新的映射表
        // 这里暂时抛出异常提示
        throw new BaseException("暂不支持标签绑定到主题");
    }

    /**
     * 从笔记解除标签绑定
     * @param tagId 标签ID
     * @param noteId 笔记ID
     * @param userId 用户ID
     */
    private void removeTagFromNote(Long tagId, Long noteId, Long userId) {
        // 校验笔记归属
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || note.getIsDeleted() == 1) {
            throw new BaseException("笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException("只能操作自己的笔记");
        }

        // 查找绑定关系
        List<NoteTagMappingEntity> mappings = noteTagMappingMapper.selectByNoteId(noteId);
        NoteTagMappingEntity targetMapping = null;
        for (NoteTagMappingEntity mapping : mappings) {
            if (mapping.getTagId() != null && mapping.getTagId().equals(tagId) &&
                mapping.getIsDeleted() == 0) {
                targetMapping = mapping;
                break;
            }
        }

        if (targetMapping == null) {
            throw new BaseException("该标签未绑定到此笔记");
        }

        // 解除绑定（软删除）
        int count = noteTagMappingMapper.unbindTagById(targetMapping.getId());
        if (count <= 0) {
            throw new BaseException("解除绑定失败");
        }
    }

    /**
     * 从主题解除标签绑定
     * @param tagId 标签ID
     * @param topicId 主题ID
     * @param userId 用户ID
     */
    private void removeTagFromTopic(Long tagId, Long topicId, Long userId) {
        // 校验主题归属
        TopicEntity topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BaseException("主题不存在");
        }
        if (!topic.getUserId().equals(userId)) {
            throw new BaseException("只能操作自己的主题");
        }

        // 注意：当前设计可能没有topic-tag映射表
        throw new BaseException("暂不支持从主题解除标签绑定");
    }

    /**
     * 规范化标签名称
     * @param tagName 标签名称
     * @return 规范化后的标签名称
     */
    private String normalizeTagName(String tagName) {
        if (tagName == null) {
            return null;
        }
        return tagName.trim();
    }
}