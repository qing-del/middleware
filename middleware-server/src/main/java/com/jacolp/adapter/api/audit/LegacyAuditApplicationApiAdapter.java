package com.jacolp.adapter.api.audit;

import com.jacolp.exception.BaseException;
import com.jacolp.mapper.ImageAuditMapper;
import com.jacolp.mapper.MetaAuditMapper;
import com.jacolp.mapper.NoteAuditMapper;
import com.jacolp.middleware.module.audit.api.AuditApplicationApi;
import com.jacolp.middleware.module.audit.api.AuditApplicationResult;
import com.jacolp.middleware.module.audit.api.AuditTargetType;
import com.jacolp.middleware.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.middleware.module.audit.api.CancelAuditApplicationResult;
import com.jacolp.middleware.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.middleware.module.audit.api.PendingAuditApplicationQuery;
import com.jacolp.pojo.entity.ImageAuditRecordEntity;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.NoteAuditRecordEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Temporary adapter that maps the stable audit API to the three legacy audit tables.
 */
@Component
public class LegacyAuditApplicationApiAdapter implements AuditApplicationApi {

    private static final short TAG_APPLY_TYPE = 2;
    private static final short TAG_AND_IMAGE_PENDING_STATUS = 1;
    private static final short NOTE_PENDING_STATUS = 0;

    private final MetaAuditMapper metaAuditMapper;
    private final ImageAuditMapper imageAuditMapper;
    private final NoteAuditMapper noteAuditMapper;

    public LegacyAuditApplicationApiAdapter(MetaAuditMapper metaAuditMapper,
                                            ImageAuditMapper imageAuditMapper,
                                            NoteAuditMapper noteAuditMapper) {
        this.metaAuditMapper = metaAuditMapper;
        this.imageAuditMapper = imageAuditMapper;
        this.noteAuditMapper = noteAuditMapper;
    }

    @Override
    public boolean hasPendingApplication(PendingAuditApplicationQuery query) {
        return switch (query.targetType()) {
            case TAG -> metaAuditMapper.countPendingAuditByApplyTypeAndTargetId(TAG_APPLY_TYPE, query.targetId()) > 0;
            case IMAGE -> imageAuditMapper.countPendingAuditByImageId(query.targetId()) > 0;
            case NOTE -> noteAuditMapper.countPendingAuditByNoteId(query.targetId()) > 0;
        };
    }

    @Override
    public AuditApplicationResult createApplication(CreateAuditApplicationCommand command) {
        return switch (command.targetType()) {
            case TAG -> createTagApplication(command);
            case IMAGE -> createImageApplication(command);
            case NOTE -> createNoteApplication(command);
        };
    }

    @Override
    public CancelAuditApplicationResult cancelApplication(CancelAuditApplicationCommand command) {
        return switch (command.targetType()) {
            case TAG -> cancelTagApplication(command);
            case IMAGE -> cancelImageApplication(command);
            case NOTE -> cancelNoteApplication(command);
        };
    }

    private AuditApplicationResult createTagApplication(CreateAuditApplicationCommand command) {
        MetaAuditRecordEntity record = new MetaAuditRecordEntity();
        record.setApplyType(TAG_APPLY_TYPE);
        record.setTargetId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(metaAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private AuditApplicationResult createImageApplication(CreateAuditApplicationCommand command) {
        ImageAuditRecordEntity record = new ImageAuditRecordEntity();
        record.setImageId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(imageAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private AuditApplicationResult createNoteApplication(CreateAuditApplicationCommand command) {
        NoteAuditRecordEntity record = new NoteAuditRecordEntity();
        record.setNoteId(command.targetId());
        record.setApplicantUserId(command.applicantUserId());
        record.setApplyReason(command.applyReason());
        ensureInserted(noteAuditMapper.insertAuditRecord(record), record.getId());
        return createdResult(command, record.getId());
    }

    private CancelAuditApplicationResult cancelTagApplication(CancelAuditApplicationCommand command) {
        MetaAuditRecordEntity record = metaAuditMapper.selectPendingByApplyTypeAndTargetId(TAG_APPLY_TYPE, command.targetId());
        ensurePendingApplicant(
                record == null ? null : record.getApplicantUserId(),
                record == null ? null : record.getStatus(),
                TAG_AND_IMAGE_PENDING_STATUS,
                command.actorUserId());
        int affected = metaAuditMapper.deletePendingByApplyTypeAndTargetId(TAG_APPLY_TYPE, command.targetId());
        return cancelledResult(command, affected);
    }

    private CancelAuditApplicationResult cancelImageApplication(CancelAuditApplicationCommand command) {
        ImageAuditRecordEntity record = imageAuditMapper.selectPendingByImageId(command.targetId());
        ensurePendingApplicant(
                record == null ? null : record.getApplicantUserId(),
                record == null ? null : record.getStatus(),
                TAG_AND_IMAGE_PENDING_STATUS,
                command.actorUserId());
        int affected = imageAuditMapper.deletePendingByImageId(command.targetId());
        return cancelledResult(command, affected);
    }

    private CancelAuditApplicationResult cancelNoteApplication(CancelAuditApplicationCommand command) {
        NoteAuditRecordEntity record = noteAuditMapper.selectPendingByNoteId(command.targetId());
        ensurePendingApplicant(
                record == null ? null : record.getApplicantUserId(),
                record == null ? null : record.getStatus(),
                NOTE_PENDING_STATUS,
                command.actorUserId());
        int affected = noteAuditMapper.deletePendingByNoteId(command.targetId());
        return cancelledResult(command, affected);
    }

    private static AuditApplicationResult createdResult(CreateAuditApplicationCommand command, Long auditApplicationId) {
        return new AuditApplicationResult(
                auditApplicationId, command.targetType(), command.targetId(), command.applicantUserId());
    }

    private static CancelAuditApplicationResult cancelledResult(CancelAuditApplicationCommand command, int affected) {
        if (affected < 1) {
            throw new BaseException("未找到待审核的申请记录");
        }
        return new CancelAuditApplicationResult(command.targetType(), command.targetId(), command.actorUserId(), affected);
    }

    private static void ensureInserted(int affected, Long auditApplicationId) {
        if (affected < 1 || auditApplicationId == null) {
            throw new BaseException("创建审核申请失败");
        }
    }

    private static void ensurePendingApplicant(Long applicantUserId,
                                               Short status,
                                               short expectedPendingStatus,
                                               Long actorUserId) {
        if (applicantUserId == null) {
            throw new BaseException("未找到待审核的申请记录");
        }
        if (!Objects.equals(status, expectedPendingStatus)) {
            throw new BaseException("审核申请未处于待审核状态");
        }
        if (!Objects.equals(applicantUserId, actorUserId)) {
            throw new BaseException("只能撤销自己的审核申请");
        }
    }
}
