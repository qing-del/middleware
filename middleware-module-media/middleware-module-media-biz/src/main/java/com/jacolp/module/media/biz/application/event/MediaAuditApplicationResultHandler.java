package com.jacolp.module.media.biz.application.event;

import com.jacolp.middleware.messaging.service.AsyncCommandStateService;
import com.jacolp.middleware.messaging.event.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.event.AuditApplicationResultEvent;
import com.jacolp.module.media.biz.infrastructure.persistence.mapper.ImageMapper;
import org.springframework.stereotype.Service;

@Service
public class MediaAuditApplicationResultHandler {
    private static final short WAITING = 0;
    private static final short AUDITING = 1;
    private final AsyncCommandStateService commandState;
    private final ImageMapper imageMapper;

    public MediaAuditApplicationResultHandler(AsyncCommandStateService commandState, ImageMapper imageMapper) {
        this.commandState = commandState; this.imageMapper = imageMapper;
    }

    public void apply(AuditApplicationResultEvent event) {
        if (event.targetType() != AuditApplicationRequestedEvent.TargetType.IMAGE) return;
        if (!commandState.completeIfCurrent("MEDIA", "IMAGE", event.targetId(), event.commandId())) return;
        if (event.outcome() == AuditApplicationResultEvent.Outcome.REJECTED) {
            imageMapper.updateAuditStatusIfCurrent(event.targetId(), AUDITING, WAITING);
        } else if (event.outcome() == AuditApplicationResultEvent.Outcome.CANCEL_REJECTED) {
            imageMapper.updateAuditStatusIfCurrent(event.targetId(), WAITING, AUDITING);
        }
    }
}
