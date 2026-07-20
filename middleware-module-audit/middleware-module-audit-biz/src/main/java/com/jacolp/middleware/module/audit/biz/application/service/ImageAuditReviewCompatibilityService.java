package com.jacolp.middleware.module.audit.biz.application.service;

import com.jacolp.constant.ImageConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.module.audit.biz.application.dto.ImageAuditReviewDTO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.middleware.module.media.api.MediaAuditApplyApi;
import com.jacolp.middleware.module.media.api.command.ApplyMediaAuditCommand;
import com.jacolp.middleware.module.media.api.model.MediaAuditDecision;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility service for the legacy single-image review endpoint. */
@Service
public class ImageAuditReviewCompatibilityService {
    private final ImageAuditMapper imageAuditMapper;
    private final MediaAuditApplyApi mediaAuditApplyApi;

    public ImageAuditReviewCompatibilityService(ImageAuditMapper imageAuditMapper, MediaAuditApplyApi mediaAuditApplyApi) {
        this.imageAuditMapper = imageAuditMapper;
        this.mediaAuditApplyApi = mediaAuditApplyApi;
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(ImageAuditReviewDTO dto) {
        if (dto == null || dto.getAuditId() == null || dto.getAuditId() <= 0) {
            throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        }
        ImageAuditRecordDO record = imageAuditMapper.selectById(dto.getAuditId());
        if (record == null || !AuditStatus.AUDITING.getCode().equals(record.getStatus())) {
            throw new BaseException(ImageConstant.IMAGE_AUDIT_ALREADY_PROCESSED);
        }
        if (!dto.getApproved() && (dto.getRejectReason() == null || dto.getRejectReason().isEmpty())) {
            throw new BaseException(ImageConstant.IMAGE_REJECT_REASON_NOT_EMPTY);
        }
        Short status = dto.getApproved() ? AuditStatus.APPROVED.getCode() : AuditStatus.REJECTED.getCode();
        record.setStatus(status);
        record.setReviewerUserId(BaseContext.getCurrentId());
        record.setReviewTime(LocalDateTime.now());
        if (!dto.getApproved()) {
            record.setRejectReason(dto.getRejectReason());
        }
        mediaAuditApplyApi.applyMediaAudit(new ApplyMediaAuditCommand(List.of(record.getImageId()),
                dto.getApproved() ? MediaAuditDecision.APPROVED : MediaAuditDecision.REJECTED, false));
        imageAuditMapper.updateAuditRecord(record);
    }
}
