package com.jacolp.service;

import com.jacolp.constant.ImageConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.module.media.api.MediaAuditApplyApi;
import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditDecision;
import com.jacolp.pojo.dto.image.ImageAuditReviewDTO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/** Temporary server compatibility seam for the legacy single-image review endpoint. */
@Service
public class ImageAuditReviewCompatibilityService {
    private final AuditService auditService; private final MediaAuditApplyApi mediaAuditApplyApi;
    public ImageAuditReviewCompatibilityService(AuditService auditService, MediaAuditApplyApi mediaAuditApplyApi) { this.auditService = auditService; this.mediaAuditApplyApi = mediaAuditApplyApi; }
    @Transactional(rollbackFor = Exception.class)
    public void review(ImageAuditReviewDTO dto) {
        if (dto == null || dto.getAuditId() == null || dto.getAuditId() <= 0) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        ImageAuditRecordDO record = auditService.getImageAuditRecordById(dto.getAuditId());
        if (record == null || !AuditStatus.AUDITING.getCode().equals(record.getStatus())) throw new BaseException(ImageConstant.IMAGE_AUDIT_ALREADY_PROCESSED);
        if (!dto.getApproved() && (dto.getRejectReason() == null || dto.getRejectReason().isEmpty())) throw new BaseException(ImageConstant.IMAGE_REJECT_REASON_NOT_EMPTY);
        Short status = dto.getApproved() ? AuditStatus.APPROVED.getCode() : AuditStatus.REJECTED.getCode();
        record.setStatus(status); record.setReviewerUserId(BaseContext.getCurrentId()); record.setReviewTime(LocalDateTime.now());
        if (!dto.getApproved()) record.setRejectReason(dto.getRejectReason());
        mediaAuditApplyApi.applyMediaAudit(new ApplyMediaAuditCommand(List.of(record.getImageId()),
                dto.getApproved() ? MediaAuditDecision.APPROVED : MediaAuditDecision.REJECTED, false));
        auditService.updateImageAuditRecord(record);
    }
}
