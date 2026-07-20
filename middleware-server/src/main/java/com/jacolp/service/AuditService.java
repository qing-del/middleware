package com.jacolp.service;

import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;

/** Compatibility seam retained only for the legacy single-image audit route. */
public interface AuditService {
    ImageAuditRecordDO getImageAuditRecordById(Long id);
    boolean hasPendingImageAudit(Long imageId);
    void createImageAuditRecord(ImageAuditRecordDO record);
    void updateImageAuditRecord(ImageAuditRecordDO record);
}
