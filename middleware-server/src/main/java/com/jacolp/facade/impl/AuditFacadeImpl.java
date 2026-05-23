package com.jacolp.facade.impl;

import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.NoteConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.facade.AuditFacade;
import com.jacolp.pojo.dto.audit.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.audit.AuditReviewContext;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import com.jacolp.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AuditFacadeImpl implements AuditFacade {

    @Autowired private AuditService auditService;

    @Autowired private TopicService topicService;
    @Autowired private TagService tagService;
    @Autowired private ImageService imageService;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteRelationService noteRelationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewMeta(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数并过滤无效 ID。
        AuditReviewContext context = validateReviewRequest(dto);

        List<MetaAuditRecordEntity> pendingRecords = auditService.batchReviewMeta(context);

        List<Long> topicIds = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        for (MetaAuditRecordEntity record : pendingRecords) {
            if (record.getApplyType() == null || record.getTargetId() == null) {
                continue;
            }
            if (record.getApplyType().equals(AuditConstant.TOPIC_APPLY_TYPE)) {
                topicIds.add(record.getTargetId());
            } else if (record.getApplyType().equals(AuditConstant.TAG_APPLY_TYPE)) {
                tagIds.add(record.getTargetId());
            }
        }

        if (!topicIds.isEmpty()) {
            topicService.updatePassStatusByIds(topicIds, context.getStatus());
        }
        if (!tagIds.isEmpty()) {
            updateTagsAndTagMappingsPass(tagIds, context.getStatus(), pendingRecords.size());
        }

        return pendingRecords.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewImage(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数并过滤无效 ID。
        AuditReviewContext context = validateReviewRequest(dto);

        List<ImageAuditRecordEntity> pendingRecords = auditService.batchReviewImage(context);

        List<Long> imageIds = pendingRecords.stream()
                .map(ImageAuditRecordEntity::getImageId)
                .filter(id -> id != null)
                .toList();
        if (!imageIds.isEmpty()) {
            updateImagesAndImageMappingsPass(imageIds, context.getStatus(), pendingRecords.size());
        }

        return pendingRecords.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReviewNote(AuditBatchReviewDTO dto) {
        // 1) 校验请求参数并过滤无效 ID。
        AuditReviewContext context = validateReviewRequest(dto);

        List<NoteAuditRecordEntity> pendingRecords = auditService.batchReviewNote(context);

        List<Long> noteIds = pendingRecords.stream()
                .map(NoteAuditRecordEntity::getNoteId)
                .filter(id -> id != null)
                .toList();

        if (!noteIds.isEmpty()) {
            updateNotesAndEachMappingsPass(noteIds, context, pendingRecords.size());
        }

        return pendingRecords.size();
    }


    /**
     * 校验批量审核请求并构造上下文。
     * <p>会做 ID 去重、审核状态合法性校验，并在拒绝场景下填充默认拒绝原因。</p>
     *
     * @param dto 批量审核参数
     * @return 审核上下文(一定是非空集合)
     */
    private AuditReviewContext validateReviewRequest(AuditBatchReviewDTO dto) {
        // 检查审核记录ID列表
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BaseException("审核记录ID列表不能为空");
        }

        // 检查审核状态是否有效
        if (!AuditConstant.PASS.equals(dto.getStatus())
                && !AuditConstant.REJECT.equals(dto.getStatus())) {
            throw new BaseException("无效的审核状态");
        }

        // 去重
        Set<Long> idSet = new LinkedHashSet<>();
        for (Long id : dto.getIds()) {
            if (id == null || id <= 0L) {
                continue;
            }
            idSet.add(id);
        }
        // 检查一下列表是否为空
        if (idSet.isEmpty()) {
            throw new BaseException("审核记录ID列表不能为空");
        }

        // 填充默认拒绝原因
        String rejectReason = null;
        if (AuditConstant.REJECT.equals(dto.getStatus())) {
            rejectReason = StringUtils.hasText(dto.getRejectReason())
                    ? dto.getRejectReason().trim()
                    : AuditConstant.DEFAULT_REJECT_REASON;
        }

        AuditReviewContext context = new AuditReviewContext();
        context.setIds(new ArrayList<>(idSet));
        context.setStatus(dto.getStatus());
        context.setReviewerUserId(BaseContext.getCurrentId());
        context.setRejectReason(rejectReason);
        return context;
    }

    /**
     * 批量审核标签及其映射行。
     * <p>先更新 biz_tag，再回写对应的 biz_note_tag_mapping，避免逐条循环更新。</p>
     *
     * @param tagIds 标签ID列表
     * @param status 审核状态
     * @param affected 本次审核表实际影响行数
     */
    private void updateTagsAndTagMappingsPass(List<Long> tagIds, Short status, int affected) {
        // 批量更新标签状态。
        int count = tagService.updatePassStatusByIds(tagIds, status);
        noteRelationService.updateTagMappingPassByTagIds(tagIds, status);
        // 保守校验：如果业务表更新行数不足，直接回滚。
        if (count < affected) {
            log.error("Failed to update tag status! : {}", tagIds);
            throw new BaseException("更新标签状态失败");
        }
    }

    /**
     * 批量审核图片及其映射行。
     * <p>先更新 biz_image，再回写对应的 biz_note_image_mapping，保持审核状态一致。</p>
     *
     * @param imageIds 图片ID列表
     * @param status 审核状态
     * @param affected 本次审核表实际影响行数
     */
    private void updateImagesAndImageMappingsPass(List<Long> imageIds, Short status, int affected) {
        // 批量更新图片状态。
        int count = imageService.updatePassStatusByIds(imageIds, status);
        noteRelationService.updateImageMappingPassByImageIds(imageIds, status);
        // 保守校验：业务表更新必须跟审核表影响行数一致。
        if (count < affected) {
            log.error("Failed to update image status! : {}", imageIds);
            throw new BaseException("更新图片状态失败");
        }
    }

    /**
     * 批量审核笔记及其内联映射。
     * <p>先更新 biz_note，再回写对应的 biz_note_each_mapping，保持审核状态一致。</p>
     * <p>同时会检查影响行数是否 和 需要被影响的行数 是否一致</p>
     * @param noteIds 笔记ID列表
     * @param context 审核上下文
     * @param affected 本次审核表实际影响行数
     * @throws BaseException 笔记更新失败
     */
    private void updateNotesAndEachMappingsPass(List<Long> noteIds, AuditReviewContext context, int affected) {
        // 批量更新笔记状态。将审核状态映射为 NoteStatus
        Short noteStatus = context.getStatus().equals(AuditConstant.PASS) ?
                NoteStatus.APPROVED.getCode() : NoteStatus.REJECTED.getCode();
        int rows = noteCoreService.updateStatusByIds(noteIds, noteStatus);
        if (rows < affected) {
            log.error("Failed to update note status! : {}", noteIds);
            throw new BaseException(NoteConstant.NOTE_UPDATE_FAILED);
        }
        noteRelationService.updateEachMappingPassBySourceNoteIds(noteIds, context.getStatus());
    }
}
