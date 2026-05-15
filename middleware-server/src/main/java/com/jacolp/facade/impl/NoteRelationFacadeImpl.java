package com.jacolp.facade.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.jacolp.constant.NoteConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.NoteStatus;
import com.jacolp.facade.NoteRelationFacade;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.NoteEachMappingEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteImageMappingEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.image.ImageSimpleVO;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.service.ImageService;
import com.jacolp.service.NoteCoreService;
import com.jacolp.service.NoteRelationService;
import com.jacolp.service.TagService;

@Service
public class NoteRelationFacadeImpl implements NoteRelationFacade {

    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteRelationService noteRelationService;

    @Autowired private TagService tagService;
    @Autowired private ImageService imageService;

    /**
     * 获取笔记三类关联映射详情（标签 / 图片 / 内联笔记）。
     * <p>通过 {@link NoteCoreService#getById} 校验所有权后委托给 {@link NoteRelationService}。</p>
     */
    @Override
    public NoteRelationDetailVO getRelationInfo(Long noteId) {
        if (!PermissionContext.isAdmin()) {
            noteCoreService.getById(noteId);    // 不是管理员才需要校验
        }

        // 先进行三联映射查询
        List<NoteTagMappingEntity> tagMappings = noteRelationService.listTagMappingsByNoteId(noteId);
        List<NoteImageMappingEntity> imageMappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<NoteEachMappingEntity> eachMappings = noteRelationService.listEachMappingsByNoteId(noteId);

        // 构建缓存
        Map<Long, TagEntity> tagMap = buildTagMap(tagMappings);
        Map<Long, ImageEntity> imageMap = buildImageMap(imageMappings);
        Map<Long, NoteEntity> targetNoteMap = buildTargetNoteMap(eachMappings);

        // 构建返回结果
        return noteRelationService.getRelationInfo(
                noteId,
                tagMappings, tagMap,
                imageMappings, imageMap,
                eachMappings, targetNoteMap);
    }

    /**
     * 获取图片简要列表
     * <p>- 此处没有权限校验</p>
     */
    @Override
    public List<ImageSimpleVO> listImageSimpleVOsByNoteId(Long noteId) {
        List<NoteImageMappingEntity> mappings = noteRelationService.listImageMappingsByNoteId(noteId);
        List<Long> imageIds = mappings
                .stream()
                .map(NoteImageMappingEntity::getImageId)
                .toList();

        Map<Long, ImageEntity> imageMap = imageIds.isEmpty()
                ? Map.of()
                : imageService.getByIds(imageIds).stream()
                  .collect(Collectors.toMap(ImageEntity::getId, image -> image, (left, right) -> left));

        List<ImageSimpleVO> result = new ArrayList<>();
        buildNoteImageSimpleVOList(noteId, mappings, imageMap, result);
        return result;
    }

    /**
     * 绑定标签映射关系
     */
    @Override
    public void bindTagMapping(TagMappingBindDTO dto) {
        TagEntity targetTag = tagService.getByIdAndUserId(dto.getTagId(), BaseContext.getCurrentId());
        NoteTagMappingEntity mapping = noteRelationService.bindTagMapping(dto, targetTag);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(mapping.getNoteId());
        if (note.getMissingCount() <= 1) {
            tryConvertNoteToReady(note);
        } else {
            note.setMissingCount(Math.max(note.getMissingCount() - 1, 0));
            if (noteRelationService.isMissingTags(note.getId())) {
                note.setMissingInfoMask(note.getMissingInfoMask() & ~NoteMissingInfoMask.TAG.getMask());
            }
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteTagMappingEntity unbindTagMapping(Long mappingId) {
        // 尝试解除绑定
        NoteTagMappingEntity result = noteRelationService.unbindTagMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(result.getNoteId());
        if (NoteConstant.STATUS_READY_TO_CONVERT.equals(note.getStatus())) {
            note.setStatus(NoteConstant.MISSED_INFO);
        }

        // 更新笔记缺失信息标记位
        note.setMissingInfoMask(note.getMissingInfoMask() | NoteMissingInfoMask.TAG.getMask());
        note.setMissingCount(note.getMissingCount() + 1);

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    /**
     * 绑定图片映射关系
     */
    @Override
    public void bindImageMapping(ImageMappingBindDTO dto) {
        ImageEntity targetImage = imageService.getById(dto.getImageId());
        NoteImageMappingEntity mapping = noteRelationService.bindImageMapping(dto, targetImage);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(mapping.getNoteId());
        if (note.getMissingCount() <= 1) {
            tryConvertNoteToReady(note);
        } else {
            note.setMissingCount(Math.max(note.getMissingCount() - 1, 0));
            if (noteRelationService.isMissingImages(note.getId())) {
                note.setMissingInfoMask(note.getMissingInfoMask() & ~NoteMissingInfoMask.IMAGE.getMask());
            }
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteImageMappingEntity unbindImageMapping(Long mappingId) {
        // 尝试解除绑定
        NoteImageMappingEntity result = noteRelationService.unbindImageMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(result.getNoteId());
        if (NoteConstant.STATUS_READY_TO_CONVERT.equals(note.getStatus())) {
            note.setStatus(NoteConstant.MISSED_INFO);
        }

        // 更新笔记缺失信息标记位
        note.setMissingInfoMask(note.getMissingInfoMask() | NoteMissingInfoMask.IMAGE.getMask());
        note.setMissingCount(note.getMissingCount() + 1);

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    /**
     * 绑定内联笔记映射关系
     */
    @Override
    public void bindEachMapping(EachMappingBindDTO dto) {
        NoteEntity targetNote = noteCoreService.getById(dto.getNoteId());
        NoteEachMappingEntity mapping = noteRelationService.bindEachMapping(dto, targetNote);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(mapping.getNoteId());
        if (note.getMissingCount() <= 1) {
            tryConvertNoteToReady(note);
        } else {
            note.setMissingCount(Math.max(note.getMissingCount() - 1, 0));
            if (noteRelationService.isMissingNotes(note.getId())) {
                note.setMissingInfoMask(note.getMissingInfoMask() & ~NoteMissingInfoMask.NOTE.getMask());
            }
        }

        // 更新笔记状态
        noteCoreService.update(note);
    }

    @Override
    public NoteEachMappingEntity unbindEachMapping(Long mappingId) {
        // 尝试解除绑定
        NoteEachMappingEntity result = noteRelationService.unbindEachMapping(mappingId);

        // 检查是否需要更新笔记状态
        NoteEntity note = noteCoreService.getById(result.getNoteId());
        if (NoteConstant.STATUS_READY_TO_CONVERT.equals(note.getStatus())) {
            note.setStatus(NoteConstant.MISSED_INFO);
        }

        // 更新笔记缺失信息标记位
        note.setMissingInfoMask(note.getMissingInfoMask() | NoteMissingInfoMask.NOTE.getMask());
        note.setMissingCount(note.getMissingCount() + 1);

        // 更新笔记
        noteCoreService.update(note);

        // 返回结果
        return result;
    }

    @Override
    public NoteCheckBindingVO checkRelationCompletion(Long noteId) {
        NoteEntity note = noteCoreService.getById(noteId);

        // 先尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
        syncBindableMappings(noteId, note.getUserId(), note.getTopicId());

        return tryConvertNoteToReady(note);
    }

    /**
     * 尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
     * <p>- 此处不会校验权限</p>
     * <p>- 使用{@link NoteRelationService#checkRelationCompletion(NoteEntity)} 去检查绑定状态</p>
     * <p>- 使用{@link NoteCoreService#update(NoteEntity)} 更新笔记</p>
     */
    private @NonNull NoteCheckBindingVO tryConvertNoteToReady(NoteEntity note) {
        // 获取笔记检查需要使用的关联信息
        NoteCheckBindingVO vo = noteRelationService.checkRelationCompletion(note);

        // 检查是否需要更新关联信息
        if (vo.isComplete()) { // 检查是否需要更新笔记状态
            NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
            NoteStatus targetStatus = NoteStatus.fromCode(NoteConstant.STATUS_READY_TO_CONVERT);
            // 检查是否可以进行转换
            if (vo.isComplete() && currentStatus.canTransitionTo(targetStatus)) {
                vo.setStatus(targetStatus.getCode());   // 转换成“可转换”状态
                vo.setStatusDesc(targetStatus.getDesc());
            } else {
                // 判断原来的笔记是不是NEW，是NEW的话发生自动转换状态
                if (NoteStatus.NEW.equals(currentStatus)) {
                    vo.setStatus(NoteStatus.READY_TO_CONVERT.getCode());
                } else {
                    vo.setStatus(currentStatus.getCode());  // 否则保持原状态
                }
                vo.setStatusDesc(currentStatus.getDesc());
            }

        }
        // 组装更新使用的数据
        note.setStatus(vo.getStatus());
        note.setMissingInfoMask(vo.getMissingInfoMask());
        note.setMissingCount(vo.getMissingCount());
        noteCoreService.update(note);
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建简单图片信息列表
     */
    private static void buildNoteImageSimpleVOList(Long noteId, List<NoteImageMappingEntity> mappings,
                                                   Map<Long, ImageEntity> imageMap, List<ImageSimpleVO> result) {
        for (NoteImageMappingEntity mapping : mappings) {
            ImageSimpleVO vo = new ImageSimpleVO();
            vo.setNoteId(noteId);
            vo.setParsedImageName(mapping.getParsedImageName());
            vo.setIsCrossUser(mapping.getIsCrossUser());
            vo.setImageId(mapping.getImageId());

            if (mapping.getImageId() == null) {
                vo.setIsMissing(NoteConstant.MISSED_INFO);
            } else {
                ImageEntity image = imageMap.get(mapping.getImageId());
                if (image != null) {
                    vo.setFilename(image.getFilename());
                    vo.setOssUrl(image.getOssUrl());
                    vo.setIsPublic(image.getIsPublic());
                    vo.setIsPass(image.getIsPass());
                    vo.setCreateTime(image.getUploadTime());
                    vo.setIsMissing(NoteConstant.NOT_MISSED_INFO);
                } else {
                    vo.setIsMissing(NoteConstant.MISSED_INFO);
                }
            }
            result.add(vo);
        }
    }

    /**
     * 同步可绑定的映射关系
     */
    private void syncBindableMappings(Long noteId, Long userId, Long topicId) {
        // 同步标签映射
        List<NoteTagMappingEntity> tagMappings = Optional.ofNullable
                (noteRelationService.listTagMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, TagEntity> tagMap = getTagEntitiesMap(tagMappings, userId);
        noteRelationService.tryBatchBindTagMappings(tagMappings, tagMap);

        if (topicId == null) return;

        // 同步图片映射
        List<NoteImageMappingEntity> imageMappings = Optional.ofNullable
                (noteRelationService.listImageMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, ImageEntity> imageMap = getImageEntitiesMap(imageMappings, userId, topicId);
        noteRelationService.tryBatchBindImageMappings(imageMappings, imageMap);

        // 同步内联笔记映射
        List<NoteEachMappingEntity> noteMappings = Optional.ofNullable
                (noteRelationService.listEachMappingsByNoteId(noteId)).orElse(List.of());
        Map<String, NoteEntity> noteMap = getNoteEntitiesMap(noteMappings, userId, topicId);
        noteRelationService.tryBatchBindNoteMappings(noteMappings, noteMap);
    }

    /**
     * 获取标签实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link TagService#getByNamesAndUserId(List, Long)} 方法</p>
     *
     * @param mappings 标签映射行
     * @param userId   用户 id
     * @return 标签实体的 <tagName, TagEntity> 的 Map
     */
    private Map<String, TagEntity> getTagEntitiesMap(List<NoteTagMappingEntity> mappings, Long userId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings
                .stream()
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<TagEntity> tags = Optional.ofNullable(tagService.getByNamesAndUserId(parsedNames, userId))
                .orElse(List.of());
        if (tags.isEmpty()) {
            return Map.of();
        }

        return tags.stream()
                .collect(Collectors
                        .toMap(
                                TagEntity::getTagName,
                                tag -> tag,
                                (left, right) -> left)
                );
    }

    /**
     * 获取图片实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link ImageService#getByUserIdAndTopicIdAndFilenames(Long, Long, List)} 方法</p>
     *
     * @param mappings 图片映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 图片实体的 <imageName, ImageEntity> 的 Map
     */
    private Map<String, ImageEntity> getImageEntitiesMap(List<NoteImageMappingEntity> mappings, Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<ImageEntity> images = Optional.ofNullable(
                        imageService.getByUserIdAndTopicIdAndFilenames(userId, topicId, parsedNames))
                .orElse(List.of());
        if (images.isEmpty()) {
            return Map.of();
        }

        return images.stream()
                .collect(Collectors
                        .toMap(
                                ImageEntity::getFilename,
                                image -> image,
                                (left, right) -> left)
                );
    }

    /**
     * 获取笔记实体
     * <p>- 如果传入空集，则返回空集</p>
     * <p>- 否则会进行批量查询，并返回结果</p>
     * <p>- 这里使用了 {@link NoteCoreService#getByUserIdAndTopicIdAndTitles(Long, Long, List)} 方法</p>
     *
     * @param mappings 笔记映射行
     * @param userId   用户 id
     * @param topicId  话题 id
     * @return 笔记实体的 <noteName, NoteEntity> 的 Map
     */
    private Map<String, NoteEntity> getNoteEntitiesMap(List<NoteEachMappingEntity> mappings,
                                                       Long userId, Long topicId) {
        if (mappings.isEmpty()) {
            return Map.of();
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return Map.of();
        }

        List<NoteEntity> notes = Optional.ofNullable(
                        noteCoreService.getByUserIdAndTopicIdAndTitles(userId, topicId, parsedNames))
                .orElse(List.of());
        if (notes.isEmpty()) {
            return Map.of();
        }

        return notes.stream()
                .collect(Collectors
                        .toMap(
                                NoteEntity::getTitle,
                                note -> note,
                                (left, right) -> left)
                );
    }

    private Map<Long, TagEntity> buildTagMap(List<NoteTagMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteTagMappingEntity::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return tagService.getByIds(ids).stream()
                .collect(Collectors.toMap(TagEntity::getId, tag -> tag, (left, right) -> left));
    }

    private Map<Long, ImageEntity> buildImageMap(List<NoteImageMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteImageMappingEntity::getImageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }
        return imageService.getByIds(ids).stream()
                .collect(Collectors.toMap(ImageEntity::getId, image -> image, (left, right) -> left));
    }

    private Map<Long, NoteEntity> buildTargetNoteMap(List<NoteEachMappingEntity> mappings) {
        List<Long> ids = mappings.stream()
                .map(NoteEachMappingEntity::getTargetNoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return noteCoreService.getByIds(ids).stream()
                .collect(Collectors.toMap(NoteEntity::getId, note -> note, (left, right) -> left));
    }
}
