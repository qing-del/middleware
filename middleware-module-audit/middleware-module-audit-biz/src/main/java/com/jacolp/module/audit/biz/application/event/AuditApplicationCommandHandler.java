package com.jacolp.module.audit.biz.application.event;

import com.jacolp.exception.BaseException;
import com.jacolp.middleware.messaging.AuditApplicationCancelRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationEventPublisher;
import com.jacolp.middleware.messaging.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationResultEvent;
import com.jacolp.module.audit.api.AuditApplicationResult;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.api.CancelAuditApplicationCommand;
import com.jacolp.module.audit.api.CreateAuditApplicationCommand;
import com.jacolp.module.audit.api.PendingAuditApplicationQuery;
import com.jacolp.module.audit.biz.application.api.AuditApplicationApiService;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditApplicationCommandHandler {
    public static final String CONSUMER_NAME = "audit.application-command";

    private final AuditApplicationApiService applications;
    private final AuditApplicationEventPublisher events;
    private final AuditQueryProjectionMapper projections;

    public AuditApplicationCommandHandler(AuditApplicationApiService applications,
                                          AuditApplicationEventPublisher events,
                                          AuditQueryProjectionMapper projections) {
        this.applications = applications;
        this.events = events;
        this.projections = projections;
    }

    public void create(AuditApplicationRequestedEvent command) {
        AuditTargetType targetType = targetType(command.targetType());
        try {
            if (applications.hasPendingApplication(new PendingAuditApplicationQuery(
                    targetType, command.targetId()))) {
                rejectCreate(command, "ALREADY_PENDING");
                return;
            }
            AuditApplicationResult created = applications.createApplication(new CreateAuditApplicationCommand(
                    targetType, command.targetId(), command.applicantUserId(), command.applyReason()));
            projections.upsertRecord(command.targetType().name(), created.auditApplicationId(),
                    command.targetId(), projections.selectUsername(command.applicantUserId()),
                    command.targetName(), command.targetUrl());
            events.result(new AuditApplicationResultEvent(command.commandId(), command.targetType(),
                    command.targetId(), AuditApplicationResultEvent.Outcome.ACCEPTED,
                    created.auditApplicationId(), null));
        } catch (BaseException rejected) {
            rejectCreate(command, "BUSINESS_REJECTED");
        }
    }

    public void cancel(AuditApplicationCancelRequestedEvent command) {
        try {
            applications.cancelApplication(new CancelAuditApplicationCommand(targetType(command.targetType()),
                    command.targetId(), command.actorUserId()));
            events.result(new AuditApplicationResultEvent(command.commandId(), command.targetType(),
                    command.targetId(), AuditApplicationResultEvent.Outcome.CANCELLED, null, null));
        } catch (BaseException rejected) {
            events.result(new AuditApplicationResultEvent(command.commandId(), command.targetType(),
                    command.targetId(), AuditApplicationResultEvent.Outcome.CANCEL_REJECTED,
                    null, "BUSINESS_REJECTED"));
        }
    }

    private void rejectCreate(AuditApplicationRequestedEvent command, String reason) {
        events.result(new AuditApplicationResultEvent(command.commandId(), command.targetType(),
                command.targetId(), AuditApplicationResultEvent.Outcome.REJECTED, null, reason));
    }

    private static AuditTargetType targetType(AuditApplicationRequestedEvent.TargetType type) {
        return AuditTargetType.valueOf(type.name());
    }
}
