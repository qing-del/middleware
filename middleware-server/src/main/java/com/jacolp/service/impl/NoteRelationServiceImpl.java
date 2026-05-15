package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jacolp.annotation.CheckMissingInfo;
import com.jacolp.pojo.dto.note.NoteMissingInfoDTO;
import com.jacolp.pojo.vo.note.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
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
import com.jacolp.service.ImageService;
import com.jacolp.service.NoteRelationService;
import com.jacolp.service.TagService;

import lombok.extern.slf4j.Slf4j;

/**
 * 笔记关联关系管理服务实现。
 * <p>负责笔记与标签、图片、内联笔记三类关联映射的绑定/解绑/校验。
 * 从 {@code NoteServiceImpl} 中解耦抽取，降低原类复杂度。</p>
 *
 * <h3>权限模型</h3>
 * <p>本服务所有公开方法均执行 <b>所有权校验</b>：调用方需提供当前登录用户的
 * JWT 身份（通过 {@link BaseContext#getCurrentId()}），方法内部通过
 *
 * <h3>缺失信息管理</h3>
 * <p>每次绑定/解绑操作后，统一通过 {@link com.jacolp.aspect.CheckMissingInfoAspect} 来处理更新
 * 重算笔记的 {@code missingInfoMask} 和 {@code missingCount}，
 * 并在信息齐全时自动将状态从 {@code PENDING_INFO} 推进到 {@code READY_TO_CONVERT}。</p>
 *
 * <h3>自动补绑定</h3>
 * <p>{@link #checkRelationCompletion(NoteEntity)} 方法触发三类映射的自动补绑定
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

    /**
     * 构建笔记关系详情
     * <p>- 使用 批量查询 和 Map 缓存避免了 O(n) 网络IO 的开销</p>
     * @return 笔记关系详情
     */
    @Override
    public NoteRelationDetailVO getRelationInfo(
            Long noteId,
            List<NoteTagMappingEntity> tagMappings, Map<Long, TagEntity> tagMap,
            List<NoteImageMappingEntity> imageMappings, Map<Long, ImageEntity> imageMap,
            List<NoteEachMappingEntity> eachMappings, Map<Long, NoteEntity> targetNoteMap) {
        // 组装返回 VO
        NoteRelationDetailVO vo = new NoteRelationDetailVO();
        vo.setNoteId(noteId);
        vo.setTags(buildTagRows(tagMappings, tagMap));
        vo.setImages(buildImageRows(imageMappings, imageMap));
        vo.setEachNotes(buildEachRows(eachMappings, targetNoteMap));
        return vo;
    }

    /**
     * 绑定标签映射。
     * <p>校验链：参数完整性 → 映射行归属 → 目标标签存在性 →
     * 名称一致性 → 标签审核状态 → 执行绑定 → 刷新缺失信息。</p>
     */
    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteTagMappingEntity bindTagMapping(TagMappingBindDTO dto, TagEntity targetTag) {
        // 1) 基础参数校验
        if (dto == null || dto.getMappingId() == null || dto.getTagId() == null) {
            throw new BaseException("映射ID和标签ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        // 2) 校验映射行归属与目标标签存在性
        NoteTagMappingEntity mapping = requireOwnedTagMapping(dto.getMappingId(), userId);

        if (!Objects.equals(mapping.getParsedTagName(), targetTag.getTagName())) {
            throw new BaseException("标签名称与映射行解析名称不一致，无法绑定");
        }
        if (!AuditConstant.PASS.equals(targetTag.getIsPass())) {
            throw new BaseException("目标标签未通过审核，无法绑定");
        }

        noteTagMappingMapper.bindTagById(mapping.getId(), targetTag.getId(), AuditConstant.PASS);
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteTagMappingEntity unbindTagMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteTagMappingEntity mapping = requireOwnedTagMapping(mappingId, userId);
        noteTagMappingMapper.unbindTagById(mappingId);
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteImageMappingEntity bindImageMapping(ImageMappingBindDTO dto, ImageEntity targetImage) {
        if (dto == null || dto.getMappingId() == null || dto.getImageId() == null) {
            throw new BaseException("映射ID和图片ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteImageMappingEntity mapping = requireOwnedImageMapping(dto.getMappingId(), userId);


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
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteImageMappingEntity unbindImageMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteImageMappingEntity mapping = requireOwnedImageMapping(mappingId, userId);
        noteImageMappingMapper.unbindImageById(mappingId);
        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteEachMappingEntity bindEachMapping(EachMappingBindDTO dto, NoteEntity targetNote) {
        if (dto == null || dto.getMappingId() == null || dto.getNoteId() == null) {
            throw new BaseException("映射ID和笔记ID不能为空");
        }
        Long userId = BaseContext.getCurrentId();

        NoteEachMappingEntity mapping = requireOwnedEachMapping(dto.getMappingId(), userId);

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

        return mapping;
    }

    @Override
    @CheckMissingInfo(enableTransaction = true)
    public NoteEachMappingEntity unbindEachMapping(Long mappingId) {
        Long userId = BaseContext.getCurrentId();
        NoteEachMappingEntity mapping = requireOwnedEachMapping(mappingId, userId);
        noteEachMappingMapper.unbindNoteById(mappingId);
        return mapping;
    }

    /**
     * 校验关联完整性，计算缺失信息。
     * <p>状态流转：NEW → PENDING_INFO → (信息齐全时) READY_TO_CONVERT。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteCheckBindingVO checkRelationCompletion(NoteEntity note) {
        NoteCheckBindingVO result = new NoteCheckBindingVO();
        result.setNoteId(note.getId());

        // 基于当前映射状态计算缺失掩码和数量
        NoteMissingInfoDTO missingInfo = scanMissingInfo(note.getId());
        int missingMask = missingInfo.getMissingMask();
        int missingCount = missingInfo.getMissingCount();

        // 如果标签缺失
        if (NoteMissingInfoMask.isTagMissing(missingMask)) {
            result.setMissingTags(getMissingTagNames(note.getId()));
        }

        // 如果图片缺失
        if (NoteMissingInfoMask.isImageMissing(missingMask)) {
            result.setMissingImages(getMissingImageNames(note.getId()));
        }

        // 如果笔记缺失
        if (NoteMissingInfoMask.isNoteMissing(missingMask)) {
            result.setMissingNoteNames(getMissingEachNoteNames(note.getId()));
        }

        boolean isComplete = missingCount == 0;

        // 组装数据
        result.setComplete(isComplete);
        result.setMissingInfoMask(missingMask);
        result.setMissingCount(missingCount);

        return result;
    }

    @Override
    public List<NoteTagMappingEntity> listTagMappingsByNoteId(Long noteId) {
        List<NoteTagMappingEntity> tagMappingEntities = noteTagMappingMapper.selectByNoteId(noteId);
        if (tagMappingEntities == null || tagMappingEntities.isEmpty()) {
            return List.of();
        }
        return tagMappingEntities;
    }

    @Override
    public List<NoteImageMappingEntity> listImageMappingsByNoteId(Long noteId) {
        List<NoteImageMappingEntity> imageMappingEntities = noteImageMappingMapper.selectByNoteId(noteId);
        if (imageMappingEntities == null || imageMappingEntities.isEmpty()) {
            return List.of();
        }
        return imageMappingEntities;
    }

    @Override
    public List<NoteEachMappingEntity> listEachMappingsByNoteId(Long noteId) {
        List<NoteEachMappingEntity> eachMappingEntities = noteEachMappingMapper.selectBySourceNoteId(noteId);
        if (eachMappingEntities == null || eachMappingEntities.isEmpty()) {
            return List.of();
        }
        return eachMappingEntities;
    }

    @Override
    public int batchInsertTagMappings(List<NoteTagMappingEntity> mappings) {
        return noteTagMappingMapper.batchInsertMappings(mappings);
    }

    @Override
    public int unbindTagMappingById(Long tagId) {
        return noteTagMappingMapper.unbindTagById(tagId);
    }

    /**
     * 尝试批量绑定标签。
     * <p>如果标签已存在且可绑定，则绑定。</p>
     * @param mappings 待绑定的标签映射行
     * @param tagMap 可供选择的标签
     * @throws BaseException 批量绑定失败
     */
    @Override
    public void tryBatchBindTagMappings(List<NoteTagMappingEntity> mappings, Map<String, TagEntity> tagMap) {
        // 待绑定的标签映射行不能为空
        if (mappings == null || mappings.isEmpty() ||
                tagMap == null || tagMap.isEmpty()) {
            return;
        }

        Long userId = BaseContext.getCurrentId();
        List<NoteTagMappingEntity> toBind = new ArrayList<>();

        // 遍历待绑定的标签映射行 检查是否有匹配的标签
        for (NoteTagMappingEntity mapping : mappings) {
            TagEntity target = tagMap.get(mapping.getParsedTagName());
            if (target == null ||
                    (userId.equals(target.getUserId()) && !AuditConstant.PASS.equals(target.getIsPass()))) {
                continue;
            }

            NoteTagMappingEntity bind = new NoteTagMappingEntity();
            bind.setId(mapping.getId());
            bind.setTagId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            toBind.add(bind);
        }

        // 批量绑定
        if (!toBind.isEmpty()) {
            int affected = noteTagMappingMapper.batchBindTagByIds(toBind);
            if (affected < toBind.size()) {
                throw new BaseException("部分标签绑定失败");
            }
        }
    }

    @Override
    public void tryBatchBindImageMappings(List<NoteImageMappingEntity> mappings, Map<String, ImageEntity> imageMap) {
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
            int affected = noteImageMappingMapper.batchBindImageByIds(toBind);
            if (affected < toBind.size()) {
                throw new BaseException("部分图片绑定失败");
            }
        }
    }

    @Override
    public void tryBatchBindNoteMappings(List<NoteEachMappingEntity> mappings, Map<String, NoteEntity> noteMap) {
        List<NoteEachMappingEntity> toBind = new ArrayList<>();
        for (NoteEachMappingEntity mapping : mappings) {
            NoteEntity target = noteMap.get(mapping.getParsedNoteName());
            if (target == null) {
                continue;
            }
            NoteStatus targetNoteStatus = NoteStatus.fromCode(target.getStatus());
            if (!NoteStatus.PUBLISHED.equals(targetNoteStatus)
            && (target.getUserId() != null && !target.getUserId().equals(BaseContext.getCurrentId()))) {
                continue;
            }

            NoteEachMappingEntity bind = new NoteEachMappingEntity();
            bind.setId(mapping.getId());
            bind.setTargetNoteId(target.getId());
            bind.setIsPass(AuditConstant.PASS);
            bind.setTargetNoteId(target.getId());
            toBind.add(bind);
        }

        if (!toBind.isEmpty()) {
            int affected = noteEachMappingMapper.batchBindNoteByIds(toBind);
            if (toBind.size() != affected)  {
                throw new BaseException("部分笔记绑定失败");
            }
        }
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

    @Override
    public int initTagBatchInsertMappings(Long noteId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return 0;
        }
        List<String> distinctTags = tags.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctTags.isEmpty()) {
            return 0;
        }
        List<NoteTagMappingEntity> mappings = new ArrayList<>();
        for (String tagName : distinctTags) {
            NoteTagMappingEntity mapping = new NoteTagMappingEntity();
            mapping.setNoteId(noteId);
            mapping.setTagId(null);
            mapping.setParsedTagName(tagName);
            mapping.setIsPass(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        return batchInsertTagMappings(mappings);
    }

    @Override
    public int initImageBatchInsertMappings(NoteEntity note, List<String> images) {
        if (images == null || images.isEmpty()) {
            return 0;
        }
        List<String> distinctImages = images.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctImages.isEmpty()) {
            return 0;
        }
        List<NoteImageMappingEntity> mappings = new ArrayList<>();
        for (String imageName : distinctImages) {
            NoteImageMappingEntity mapping = new NoteImageMappingEntity();
            mapping.setNoteId(note.getId());
            mapping.setNoteTitle(note.getTitle());
            mapping.setNoteUserId(note.getUserId());
            mapping.setParsedImageName(imageName);
            mapping.setImageId(null);
            mapping.setIsCrossUser(NoteConstant.NOT_IS_CROSS_USER); // 默认先设置为没有跨用户
            mapping.setIsPass(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        noteImageMappingMapper.batchInsertMappings(mappings);
        return mappings.size();
    }

    @Override
    public int initNoteBatchInsertMappings(Long noteId, List<String> noteTitles) {
        if (noteTitles == null || noteTitles.isEmpty()) {
            return 0;
        }
        List<String> distinctTitles = noteTitles.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctTitles.isEmpty()) {
            return 0;
        }
        List<NoteEachMappingEntity> mappings = new ArrayList<>();
        for (String noteName : distinctTitles) {
            NoteEachMappingEntity mapping = new NoteEachMappingEntity();
            mapping.setSourceNoteId(noteId);
            mapping.setTargetNoteId(null);
            mapping.setParsedNoteName(noteName);
            mapping.setIsPass(AuditConstant.WAIT);
            mapping.setIsDeleted(NoteConstant.NOT_DELETED);
            mapping.setCreateTime(java.time.LocalDateTime.now());
            mappings.add(mapping);
        }
        noteEachMappingMapper.batchInsertMappings(mappings);
        return mappings.size();
    }

    @Override
    public boolean isRelated(Long id, Short type) {
        return isRelatedAll(List.of(id), type);
    }

    @Override
    public boolean isRelatedAll(List<Long> ids, Short type) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // type: 1-专题, 2-标签, 3-图片, 4-笔记
        // TODO 全局的默认策略就是被引用了不得被删除(*)
        return false;
    }

    @Override
    public NoteMissingInfoDTO computeNoteMissingInfo(Long noteId) {
        return scanMissingInfo(noteId);
    }

    @Override
    public boolean countByNoteIdAndPass(Long noteId) {
        return noteTagMappingMapper.countByNoteIdAndPass(noteId, AuditConstant.PASS)
                == noteTagMappingMapper.countByNoteIdAndPass(noteId, null)
                && noteImageMappingMapper.countByNoteIdAndPass(noteId, AuditConstant.PASS)
                == noteImageMappingMapper.countByNoteIdAndPass(noteId, null)
                && noteEachMappingMapper.countByNoteIdAndPass(noteId, AuditConstant.PASS)
                == noteEachMappingMapper.countByNoteIdAndPass(noteId, null);
    }

    @Override
    public List<NoteSimpleVO> listNoteSimplesByImageId(Long imageId) {
        return noteImageMappingMapper.selectNoteSimpleByImageId(imageId);
    }

    @Override
    public long countRelationByTagId(Long tagId) {
        return noteTagMappingMapper.countByTagId(tagId);
    }

    @Override
    public void deleteByNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        noteTagMappingMapper.softDeleteByNoteIds(noteIds);
        noteImageMappingMapper.softDeleteByNoteIds(noteIds);
        noteEachMappingMapper.softDeleteBySourceNoteIds(noteIds);
    }

    // ===== 私有方法 =====

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

    /**
     * 获取笔记的标签映射行
     * <p>- 使用 join 联查判别 userId 是否有归属权</p>
     * @param mappingId 笔记ID
     * @return 标签映射行列表
     * @throws BaseException 笔记映射行不存在 / 没有笔记所属权
     */
    private NoteTagMappingEntity requireOwnedTagMapping(Long mappingId, Long userId) {
        NoteTagMappingEntity mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteTagMappingMapper.selectById(mappingId);
        } else {
            mapping = noteTagMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }

        if (mapping == null) {
            throw new BaseException("标签映射行不存在");
        }
        return mapping;
    }

    /**
     * 获取笔记的图片映射行并校验归属权
     * <p>- 使用 note_user_id 列联查判别归属权</p>
     */
    private NoteImageMappingEntity requireOwnedImageMapping(Long mappingId, Long userId) {
        NoteImageMappingEntity mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteImageMappingMapper.selectById(mappingId);
        } else {
            mapping = noteImageMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }
        if (mapping == null) {
            throw new BaseException("图片映射行不存在");
        }
        return mapping;
    }

    /**
     * 获取笔记的内联笔记映射行并校验归属权
     * <p>- 使用 join 联查 biz_note 表判别归属权</p>
     */
    private NoteEachMappingEntity requireOwnedEachMapping(Long mappingId, Long userId) {
        NoteEachMappingEntity mapping;
        if (PermissionContext.isAdmin()) {
            mapping = noteEachMappingMapper.selectById(mappingId);
        } else {
            mapping = noteEachMappingMapper.selectByIdWithValidUserId(mappingId, userId);
        }
        if (mapping == null) {
            throw new BaseException("笔记映射行不存在");
        }
        return mapping;
    }

    private NoteMissingInfoDTO scanMissingInfo(Long noteId) {
        int missingMask = 0;
        int missingCount = 0;

        int missingTagCount = noteTagMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingTagCount > 0) {
            missingMask |= NoteConstant.MISSING_TAG;
            missingCount += missingTagCount;
        }

        int missingImageCount = noteImageMappingMapper.countByNoteIdAndImageIdIsNull(noteId);
        if (missingImageCount > 0) {
            missingMask |= NoteConstant.MISSING_IMAGE;
            missingCount += missingImageCount;
        }

        int missingNoteCount = noteEachMappingMapper.countByNoteIdAndTargetIdIsNull(noteId);
        if (missingNoteCount > 0) {
            missingMask |= NoteConstant.MISSING_NOTE;
            missingCount += missingNoteCount;
        }

        return new NoteMissingInfoDTO(missingMask, missingCount);
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
