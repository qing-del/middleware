package com.jacolp.service;

import com.jacolp.constant.ImageConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.module.media.biz.infrastructure.persistence.dataobject.ImageDO;
import com.jacolp.middleware.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import com.jacolp.pojo.dto.image.ImageAuditReviewDTO;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** Temporary server compatibility seam for the legacy single-image review endpoint. */
@Service
public class ImageAuditReviewCompatibilityService {
    private final AuditService auditService; private final ImageMapper imageMapper;
    public ImageAuditReviewCompatibilityService(AuditService auditService, ImageMapper imageMapper) { this.auditService = auditService; this.imageMapper = imageMapper; }
    @Transactional(rollbackFor = Exception.class)
    public void review(ImageAuditReviewDTO dto) {
        if (dto == null || dto.getAuditId() == null || dto.getAuditId() <= 0) throw new BaseException(ImageConstant.IMAGE_NOT_FOUND);
        ImageAuditRecordEntity record = auditService.getImageAuditRecordById(dto.getAuditId());
        if (record == null || !AuditStatus.AUDITING.getCode().equals(record.getStatus())) throw new BaseException(ImageConstant.IMAGE_AUDIT_ALREADY_PROCESSED);
        if (!dto.getApproved() && (dto.getRejectReason() == null || dto.getRejectReason().isEmpty())) throw new BaseException(ImageConstant.IMAGE_REJECT_REASON_NOT_EMPTY);
        Short status = dto.getApproved() ? AuditStatus.APPROVED.getCode() : AuditStatus.REJECTED.getCode();
        record.setStatus(status); record.setReviewerUserId(BaseContext.getCurrentId()); record.setReviewTime(LocalDateTime.now());
        if (!dto.getApproved()) record.setRejectReason(dto.getRejectReason());
        ImageDO image = imageMapper.selectById(record.getImageId());
        if (image != null) { image.setAuditStatus(status); imageMapper.updateImage(image); }
        auditService.updateImageAuditRecord(record);
    }
}
