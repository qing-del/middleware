package com.jacolp.service.impl;

import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.dataobject.ImageAuditRecordDO;
import com.jacolp.middleware.module.audit.biz.infrastructure.persistence.mapper.ImageAuditMapper;
import com.jacolp.service.AuditService;
import org.springframework.stereotype.Service;

/** Compatibility seam retained only for the legacy single-image audit route. */
@Service
public class AuditServiceImpl implements AuditService {
    private final ImageAuditMapper imageAuditMapper;

    public AuditServiceImpl(ImageAuditMapper imageAuditMapper) {
        this.imageAuditMapper = imageAuditMapper;
    }

    @Override
    public ImageAuditRecordDO getImageAuditRecordById(Long id) {
        return imageAuditMapper.selectById(id);
    }

    @Override
    public boolean hasPendingImageAudit(Long imageId) {
        return imageAuditMapper.countPendingAuditByImageId(imageId) > 0;
    }

    @Override
    public void createImageAuditRecord(ImageAuditRecordDO record) {
        imageAuditMapper.insertAuditRecord(record);
    }

    @Override
    public void updateImageAuditRecord(ImageAuditRecordDO record) {
        imageAuditMapper.updateAuditRecord(record);
    }
}
