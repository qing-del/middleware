package com.jacolp.service.impl;

import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.NoteMissingInfoMask;
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.NoteEachMappingMapper;
import com.jacolp.mapper.NoteImageMappingMapper;
import com.jacolp.mapper.NoteMapper;
import com.jacolp.mapper.NoteTagMappingMapper;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.entity.ImageEntity;
import com.jacolp.pojo.entity.NoteEachMappingEntity;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.entity.NoteImageMappingEntity;
import com.jacolp.pojo.entity.NoteTagMappingEntity;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteEachMappingRowVO;
import com.jacolp.pojo.vo.note.NoteImageMappingRowVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.pojo.vo.note.NoteTagMappingRowVO;
import com.jacolp.service.ImageService;
import com.jacolp.service.NoteRelationService;
import com.jacolp.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 笔记关联关系管理服务实现。
 * <p>负责笔记与标签、图片、内联笔记三类关联映射的绑定/解绑/校验。
 * 从 {@code NoteServiceImpl} 中解耦抽取，降低原类复杂度。</p>
 *
 * <h3>权限模型</h3>
 * <p>本服务所有公开方法均执行 <b>所有权校验</b>：调用方需提供当前登录用户的
 * JWT 身份（通过 {@link BaseContext#getCurrentId()}），方法内部通过
 * {@link #validateOwnedNote(Long, Long)} 验证笔记归属。</p>
 *
 * <h3>缺失信息管理</h3>
 * <p>每次绑定/解绑操作后，统一通过 {@link #refreshNoteMissingInfo(Long)}
 * 重算笔记的 {@code missingInfoMask} 和 {@code missingCount}，
 * 并在信息齐全时自动将状态从 {@code PENDING_INFO} 推进到 {@code READY_TO_CONVERT}。</p>
 *
 * <h3>自动补绑定</h3>
 * <p>{@link #checkRelationCompletion(Long)} 方法触发三类映射的自动补绑定
 * （{@link #syncBindableMappings}），通过批量查询命中已存在且可绑定的
 * 标签/图片/笔记，减少用户手工绑定操作。</p>
 *
 * @see NoteRelationService
 */
@Service
@Slf4j
public class NoteRelationServiceImpl implements NoteRelationService {

    @Autowired private NoteTagMappingMapper noteTagMappingMapper;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;

    // 来自其他模块的 Service
    @Autowired private NoteMapper noteMapper;
    @Autowired private TagService tagService;
    @Autowired private ImageService imageService;

    @Override
    public NoteRelationDetailVO getRelationInfo(Long noteId) {
        validateOwnedNote(noteId, BaseContext.getCurrentId());
        return buildNoteRelationDetail(noteId);
    }

    /**
     * 绑定标签映射。
     * <p>校验链：参数完整性 → 映射行归属 → 目标标签存在性 →
     * 名称一致性 → 标签审核状态 → 执行绑定 → 刷新缺失信息。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindTagMapping(TagMappingBindDTO dto, TagEntity targetTag) {
        // 1) 基础参数校验
        if (dto == null || dto.getMappingId() == null || dto.getTagId() == null) {
            throw new BaseException("映射ID和标签ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射行归属与目标标签存在性
        NoteTagMappingEntity mapping = requireOwnedTagMapping(dto.getMappingId(), userId);
//        TagEntity targetTag = tagService.getByIdAndUserId(dto.getTagId(), userId);
//        if (targetTag == null) {
//            throw new BaseException("目标标签不存在");
//        }

        if (!Objects.equals(mapping.getParsedTagName(), targetTag.getTagName())) {
            throw new BaseException("标签名称与映射行解析名称不一致，无法绑定");
        }
        if (!AuditConstant.PASS.equals(targetTag.getIsPass())) {
            throw new BaseException("目标标签未通过审核，无法绑定");
        }

        noteTagMappingMapper.bindTagById(mapping.getId(), targetTag.getId(), AuditConstant.PASS);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindTagMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteTagMappingEntity mapping = requireOwnedTagMapping(mappingId, userId);
        noteTagMappingMapper.unbindTagById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindImageMapping(ImageMappingBindDTO dto, ImageEntity targetImage) {
        if (dto == null || dto.getMappingId() == null || dto.getImageId() == null) {
            throw new BaseException("映射ID和图片ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteImageMappingEntity mapping = requireOwnedImageMapping(dto.getMappingId(), userId);
//        ImageEntity targetImage = imageService.getById(dto.getImageId());
//        if (targetImage == null) {
//            throw new BaseException("目标图片不存在");
//        }

        if (!Objects.equals(mapping.getParsedImageName(), targetImage.getFilename())) {
            throw new BaseException("图片名称与映射行解析名称不一致，无法绑定");
        }
        if (!userId.equals(targetImage.getUserId())) {
            if (!AuditConstant.PASS.equals(targetImage.getIsPass())) {
                throw new BaseException("目标图片未通过审核，无法绑定");
            }
        }

        Short isCrossUser = targetImage.getUserId() != null && !targetImage.getUserId().equals(mapping.getNoteUserId())
                ? NoteConstant.IS_CROSS_USER
                : NoteConstant.NOT_IS_CROSS_USER;

        noteImageMappingMapper.bindImageById(mapping.getId(), targetImage.getId(), targetImage.getUserId(), isCrossUser, targetImage.getIsPass());
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindImageMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteImageMappingEntity mapping = requireOwnedImageMapping(mappingId, userId);
        noteImageMappingMapper.unbindImageById(mappingId);
        refreshNoteMissingInfo(mapping.getNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindEachMapping(EachMappingBindDTO dto, NoteEntity targetNote) {
        if (dto == null || dto.getMappingId() == null || dto.getNoteId() == null) {
            throw new BaseException("映射ID和笔记ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteEachMappingEntity mapping = requireOwnedEachMapping(dto.getMappingId(), userId);
//        NoteEntity targetNote = noteMapper.selectById(dto.getNoteId());
//        if (targetNote == null || NoteStatus.fromCode(targetNote.getStatus()).isDeleted()) {
//            throw new BaseException("目标笔记不存在");
//        }

        if (!Objects.equals(mapping.getParsedNoteName(), targetNote.getTitle())) {
            throw new BaseException("笔记标题与映射行解析名称不一致，无法绑定");
        }

        // 如果笔记不属于建立绑定的人 需要笔记审核状态
        NoteStatus targetStatus = NoteStatus.fromCode(targetNote.getStatus());
        if (!Objects.equals(targetNote.getUserId(), BaseContext.getCurrentId())
                && (!targetStatus.isApproved() && !targetStatus.isPublished())) {
            throw new BaseException("目标笔记未通过审核，无法绑定");
        }

        noteEachMappingMapper.bindNoteById(mapping.getId(), targetNote.getId(), AuditConstant.PASS);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindEachMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteEachMappingEntity mapping = requireOwnedEachMapping(mappingId, userId);
        noteEachMappingMapper.unbindNoteById(mappingId);
        refreshNoteMissingInfo(mapping.getSourceNoteId());
    }

    /**
     * 校验关联完整性，自动补绑定 + 计算缺失信息。
     * <p>状态流转：NEW → PENDING_INFO → (信息齐全时) READY_TO_CONVERT。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteCheckBindingVO checkRelationCompletion(Long noteId) {
        Long userId = BaseContext.getCurrentId();
        NoteEntity note = validateOwnedNote(noteId, userId);

        NoteCheckBindingVO result = new NoteCheckBindingVO();
        result.setNoteId(noteId);

        NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());

        // 先尝试自动补绑定已存在且可绑定的标签/图片/内联笔记
        syncBindableMappings(noteId, userId, note.getTopicId());

        // 基于当前映射状态计算缺失掩码和数量
        int missingMask = calculateMissingMaskFromRelations(noteId);
        int missingCount = countInitMissingBits(missingMask);

        List<String> missingTags = getMissingTagNames(noteId);
        List<String> missingImages = getMissingImageNames(noteId);
        List<String> missingNoteNames = getMissingEachNoteNames(noteId);

        boolean isComplete = missingCount == 0;

        NoteStatus targetStatus = currentStatus;
        if (currentStatus == NoteStatus.NEW) {
            targetStatus = NoteStatus.PENDING_INFO;
        } else if (isComplete && currentStatus == NoteStatus.PENDING_INFO) {
            targetStatus = NoteStatus.READY_TO_CONVERT;
        }

        // TODO 这里将数据回传交给 NoteFacade 进行调度处理(*)
        noteMapper.updateNoteFieldsForCheck(noteId, targetStatus.getCode(), missingMask, missingCount);

        result.setStatus(targetStatus.getCode());
        result.setStatusDesc(targetStatus.getDesc());
        result.setComplete(isComplete);
        result.setMissingTags(missingTags);
        result.setMissingImages(missingImages);
        result.setMissingNoteNames(missingNoteNames);

        return result;
    }

    @Override
    public List<NoteTagMappingEntity> listTagMappingsByNoteId(Long noteId) {
        return noteTagMappingMapper.selectByNoteId(noteId);
    }

    @Override
    public int batchInsertTagMappings(List<NoteTagMappingEntity> mappings) {
        return noteTagMappingMapper.batchInsertMappings(mappings);
    }

    @Override
    public int unbindTagMappingById(Long tagId) {
        return noteTagMappingMapper.unbindTagById(tagId);
    }

    @Override
    public void updateTagMappingPassByTagIds(List<Long> tagIds, Short status) {
        noteTagMappingMapper.updateByTagIds(tagIds, status);
    }

    @Override
    public void updateImageMappingPassByImageIds(List<Long> imageIds, Short status) {
        noteImageMappingMapper.updateByImageIds(imageIds, status);
    }

    @Override
    public void updateEachMappingPassBySourceNoteIds(List<Long> sourceNoteIds, Short status) {
        noteEachMappingMapper.updateBySourceNoteIds(sourceNoteIds, status);
    }

    // ===== 私有方法 =====

    /**
     * 验证笔记所有权。本方法为 {@link NoteServiceImpl} 中同名方法的副本，
     * 避免 NoteRelationServiceImpl → NoteServiceImpl 的循环依赖。
     *
     * @return 笔记实体（保证非 null）
     * @throws BaseException 笔记不存在/已删除/不属于当前用户时抛出
     */
    private NoteEntity validateOwnedNote(Long noteId, Long userId) {    // TODO 删除掉这个方法，由上层 Facade 搞定(*)
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null || NoteStatus.fromCode(note.getStatus()).isDeleted()) {
            throw new BaseException(NoteConstant.NOTE_NOT_FOUND);
        }
        if (!note.getUserId().equals(userId)) {
            throw new BaseException(UserConstant.PERMISSION_DENIED);
        }
        return note;
    }

    /**
     * 批量补绑定调度器：依次尝试补绑定标签、图片、内联笔记三类映射。
     * <p>每种映射的补绑定策略是独立的：命中已存在的资源则自动回写映射行，
     * 未命中的保留 {@code target_id = null} 等待用户手动绑定。</p>
     */
    private void syncBindableMappings(Long noteId, Long userId, Long topicId) {
        syncBindableTagMappings(noteId, userId);
        syncBindableImageMappings(noteId, userId, topicId);
        syncBindableEachMappings(noteId, userId, topicId);
    }

    private void syncBindableTagMappings(Long noteId, Long userId) {
        List<NoteTagMappingEntity> mappings = Optional.ofNullable(noteTagMappingMapper.selectByNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<TagEntity> tags = Optional.ofNullable(tagService.getByNamesAndUserId(parsedNames, userId))
                .orElse(List.of());
        if (tags.isEmpty()) {
            return;
        }

        Map<String, TagEntity> tagMap = tags.stream()
                .collect(Collectors.toMap(TagEntity::getTagName, tag -> tag, (left, right) -> left));

        List<NoteTagMappingEntity> toBind = new ArrayList<>();
        for (NoteTagMappingEntity mapping : mappings) {
            TagEntity target = tagMap.get(mapping.getParsedTagName());
            if (target == null || !AuditConstant.PASS.equals(target.getIsPass())) {
                continue;
            }
            if (Objects.equals(mapping.getTagId(), target.getId())
                    && AuditConstant.PASS.equals(mapping.getIsPass())) {
                continue;
            }

            NoteTagMappingEntity bind = new NoteTagMappingEntity();
            bind.setId(mapping.getId());
            bind.setTagId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteTagMappingMapper.batchBindTagByIds(toBind);
        }
    }

    private void syncBindableImageMappings(Long noteId, Long userId, Long topicId) {
        if (topicId == null) {
            return;
        }

        List<NoteImageMappingEntity> mappings = Optional.ofNullable(noteImageMappingMapper.selectByNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<ImageEntity> images = Optional.ofNullable(
                imageService.getByUserIdAndTopicIdAndFilenames(userId, topicId, parsedNames))
                .orElse(List.of());
        if (images.isEmpty()) {
            return;
        }

        Map<String, ImageEntity> imageMap = images.stream()
                .collect(Collectors.toMap(ImageEntity::getFilename, image -> image, (left, right) -> left));

        List<NoteImageMappingEntity> toBind = new ArrayList<>();
        for (NoteImageMappingEntity mapping : mappings) {
            ImageEntity target = imageMap.get(mapping.getParsedImageName());
            if (target == null) {
                continue;
            }

            boolean isCrossUser = target.getUserId() != null && !target.getUserId().equals(mapping.getNoteUserId());
            if (isCrossUser && !AuditConstant.PASS.equals(target.getIsPass())) {
                continue;
            }

            Short crossUserFlag = isCrossUser ? NoteConstant.IS_CROSS_USER : NoteConstant.NOT_IS_CROSS_USER;
            boolean alreadyBound = Objects.equals(mapping.getImageId(), target.getId())
                    && Objects.equals(mapping.getImageUserId(), target.getUserId())
                    && Objects.equals(mapping.getIsCrossUser(), crossUserFlag)
                    && Objects.equals(mapping.getIsPass(), target.getIsPass());
            if (alreadyBound) {
                continue;
            }

            NoteImageMappingEntity bind = new NoteImageMappingEntity();
            bind.setId(mapping.getId());
            bind.setImageId(target.getId());
            bind.setImageUserId(target.getUserId());
            bind.setIsCrossUser(crossUserFlag);
            bind.setIsPass(target.getIsPass());
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteImageMappingMapper.batchBindImageByIds(toBind);
        }
    }

    private void syncBindableEachMappings(Long noteId, Long userId, Long topicId) {
        if (topicId == null) {
            return;
        }

        List<NoteEachMappingEntity> mappings = Optional.ofNullable(noteEachMappingMapper.selectBySourceNoteId(noteId))
                .orElse(List.of());
        if (mappings.isEmpty()) {
            return;
        }

        List<String> parsedNames = mappings.stream()
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (parsedNames.isEmpty()) {
            return;
        }

        List<NoteEntity> notes = Optional.ofNullable(
                noteMapper.selectByUserIdAndTopicIdAndTitles(userId, topicId, parsedNames))
                .orElse(List.of());
        if (notes.isEmpty()) {
            return;
        }

        Map<String, NoteEntity> noteMap = notes.stream()
                .collect(Collectors.toMap(NoteEntity::getTitle, note -> note, (left, right) -> left));

        List<NoteEachMappingEntity> toBind = new ArrayList<>();
        for (NoteEachMappingEntity mapping : mappings) {
            NoteEntity target = noteMap.get(mapping.getParsedNoteName());
            if (target == null) {
                continue;
            }
            NoteStatus targetNoteStatus = NoteStatus.fromCode(target.getStatus());
            if (!targetNoteStatus.isApproved() && !targetNoteStatus.isPublished()) {
                continue;
            }
            if (Objects.equals(mapping.getTargetNoteId(), target.getId())
                    && AuditConstant.PASS.equals(mapping.getIsPass())) {
                continue;
            }

            NoteEachMappingEntity bind = new NoteEachMappingEntity();
            bind.setId(mapping.getId());
            bind.setTargetNoteId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            noteEachMappingMapper.batchBindNoteByIds(toBind);
        }
    }

    private NoteRelationDetailVO buildNoteRelationDetail(Long noteId) {
        List<NoteTagMappingEntity> tagMappings =
                Optional.ofNullable(noteTagMappingMapper.selectByNoteId(noteId)).orElse(List.of());
        List<NoteImageMappingEntity> imageMappings =
                Optional.ofNullable(noteImageMappingMapper.selectByNoteId(noteId)).orElse(List.of());
        List<NoteEachMappingEntity> eachMappings =
                Optional.ofNullable(noteEachMappingMapper.selectBySourceNoteId(noteId)).orElse(List.of());

        Map<Long, TagEntity> tagMap = buildTagMap(tagMappings);
        Map<Long, ImageEntity> imageMap = buildImageMap(imageMappings);
        Map<Long, NoteEntity> targetNoteMap = buildTargetNoteMap(eachMappings);

        NoteRelationDetailVO vo = new NoteRelationDetailVO();
        vo.setNoteId(noteId);
        vo.setTags(buildTagRows(tagMappings, tagMap));
        vo.setImages(buildImageRows(imageMappings, imageMap));
        vo.setEachNotes(buildEachRows(eachMappings, targetNoteMap));
        return vo;
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
        return noteMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(NoteEntity::getId, note -> note, (left, right) -> left));
    }

    private List<NoteTagMappingRowVO> buildTagRows(List<NoteTagMappingEntity> mappings, Map<Long, TagEntity> tagMap) {
        return mappings.stream().map(mapping -> {
            TagEntity tag = mapping.getTagId() == null ? null : tagMap.get(mapping.getTagId());
            boolean validBind = mapping.getTagId() != null
                    && tag != null
                    && Objects.equals(mapping.getParsedTagName(), tag.getTagName());

            NoteTagMappingRowVO row = new NoteTagMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setTagId(mapping.getTagId());
            row.setParsedTagName(mapping.getParsedTagName());
            row.setTagName(tag == null ? null : tag.getTagName());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    private List<NoteImageMappingRowVO> buildImageRows(List<NoteImageMappingEntity> mappings, Map<Long, ImageEntity> imageMap) {
        return mappings.stream().map(mapping -> {
            ImageEntity image = mapping.getImageId() == null ? null : imageMap.get(mapping.getImageId());
            boolean validBind = mapping.getImageId() != null
                    && image != null
                    && Objects.equals(mapping.getParsedImageName(), image.getFilename());

            NoteImageMappingRowVO row = new NoteImageMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setNoteId(mapping.getNoteId());
            row.setImageId(mapping.getImageId());
            row.setParsedImageName(mapping.getParsedImageName());
            row.setFilename(image == null ? null : image.getFilename());
            row.setIsCrossUser(mapping.getIsCrossUser());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    private List<NoteEachMappingRowVO> buildEachRows(List<NoteEachMappingEntity> mappings, Map<Long, NoteEntity> noteMap) {
        return mappings.stream().map(mapping -> {
            NoteEntity target = mapping.getTargetNoteId() == null ? null : noteMap.get(mapping.getTargetNoteId());
            boolean validBind = mapping.getTargetNoteId() != null
                    && target != null
                    && !NoteStatus.fromCode(target.getStatus()).isDeleted()
                    && Objects.equals(mapping.getParsedNoteName(), target.getTitle());

            NoteEachMappingRowVO row = new NoteEachMappingRowVO();
            row.setMappingId(mapping.getId());
            row.setSourceNoteId(mapping.getSourceNoteId());
            row.setTargetNoteId(mapping.getTargetNoteId());
            row.setParsedNoteName(mapping.getParsedNoteName());
            row.setTargetNoteTitle(target == null ? null : target.getTitle());
            row.setAnchor(mapping.getAnchor());
            row.setNickname(mapping.getNickname());
            row.setIsPass(mapping.getIsPass());
            row.setIsMissing(validBind ? NoteConstant.NOT_MISSED_INFO : NoteConstant.MISSED_INFO);
            return row;
        }).toList();
    }

    private NoteTagMappingEntity requireOwnedTagMapping(Long mappingId, Long userId) {
        NoteTagMappingEntity mapping = noteTagMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("标签映射行不存在");
        }
        validateOwnedNote(mapping.getNoteId(), userId);
        return mapping;
    }

    private NoteImageMappingEntity requireOwnedImageMapping(Long mappingId, Long userId) {
        NoteImageMappingEntity mapping = noteImageMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("图片映射行不存在");
        }
        validateOwnedNote(mapping.getNoteId(), userId);
        return mapping;
    }

    private NoteEachMappingEntity requireOwnedEachMapping(Long mappingId, Long userId) {
        NoteEachMappingEntity mapping = noteEachMappingMapper.selectById(mappingId);
        if (mapping == null) {
            throw new BaseException("笔记映射行不存在");
        }
        validateOwnedNote(mapping.getSourceNoteId(), userId);
        return mapping;
    }

    /**
     * 绑定/解绑后统一重算缺失信息，并在信息齐全时自动推进笔记状态。
     */
    private void refreshNoteMissingInfo(Long noteId) {
        // 从当前映射表中统计缺失情况
        int missingMask = calculateMissingMaskFromRelations(noteId);
        int missingCount = countInitMissingBits(missingMask);

        // 原子更新 missing_info_mask 和 missing_count
        noteMapper.updateMissingInfoFields(noteId, missingMask, missingCount);

        // 如果信息已齐全，自动将状态从 PENDING_INFO → READY_TO_CONVERT
        checkAndAutoTransitionIfComplete(noteId);
    }

    /**
     * 根据三类映射表中 target_id 为 null 的行数计算缺失位掩码。
     */
    private int calculateMissingMaskFromRelations(Long noteId) {
        int missingMask = 0;

        long missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
        }

        long missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
        }

        long missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
        }

        return missingMask;
    }

    private int scanMissingInfo(Long noteId) {
        int missingMask = 0;

        long missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
        }

        long missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
        }

        long missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
        }

        return missingMask;
    }

    /**
     * 当 missing_count = 0 时，重新扫描确认信息齐全后，
     * 自动将状态从 PENDING_INFO 推进到 READY_TO_CONVERT。
     */
    private void checkAndAutoTransitionIfComplete(Long noteId) {
        NoteEntity note = noteMapper.selectById(noteId);
        if (note == null) {
            return;
        }

        // 仅在 missing_count 归零时触发二次扫描确认
        if (note.getMissingCount() != null && note.getMissingCount() == 0) {
            int missingMask = scanMissingInfo(noteId);
            int missingCount = countInitMissingBits(missingMask);

            noteMapper.updateMissingInfoFields(noteId, missingMask, missingCount);

            if (missingCount == 0) {
                NoteStatus currentStatus = NoteStatus.fromCode(note.getStatus());
                if (currentStatus == NoteStatus.PENDING_INFO) {
                    noteMapper.updateStatus(noteId, NoteStatus.READY_TO_CONVERT.getCode());
                }
            }
        }
    }

    private int countInitMissingBits(int missingMask) {
        int count = 0;
        if (NoteMissingInfoMask.isTagMissing(missingMask)) count++;
        if (NoteMissingInfoMask.isImageMissing(missingMask)) count++;
        if (NoteMissingInfoMask.isNoteMissing(missingMask)) count++;
        return count;
    }

    private List<String> getMissingTagNames(Long noteId) {
        List<NoteTagMappingEntity> mappings = noteTagMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTagId() == null)
                .map(NoteTagMappingEntity::getParsedTagName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> getMissingImageNames(Long noteId) {
        List<NoteImageMappingEntity> mappings = noteImageMappingMapper.selectByNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getImageId() == null)
                .map(NoteImageMappingEntity::getParsedImageName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> getMissingEachNoteNames(Long noteId) {
        List<NoteEachMappingEntity> mappings = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(m -> m.getTargetNoteId() == null)
                .map(NoteEachMappingEntity::getParsedNoteName)
                .filter(StringUtils::hasText)
                .toList();
    }
}
