package com.jacolp.module.note.biz.application.event;

import com.jacolp.middleware.messaging.event.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.service.AsyncCommandStateService;
import com.jacolp.middleware.messaging.event.AuditApplicationResultEvent;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import org.springframework.stereotype.Service;

@Service
public class NoteAuditApplicationResultHandler {
    private static final short NOTE_CONVERTED = 2;
    private static final short NOTE_PENDING = 4;
    private static final short RESOURCE_WAITING = 0;
    private static final short RESOURCE_AUDITING = 1;

    private final AsyncCommandStateService commandState;
    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;

    public NoteAuditApplicationResultHandler(AsyncCommandStateService commandState,
            NoteMapper noteMapper, TagMapper tagMapper) {
        this.commandState = commandState; this.noteMapper = noteMapper; this.tagMapper = tagMapper;
    }

    public void apply(AuditApplicationResultEvent event) {
        if (event.targetType() == AuditApplicationRequestedEvent.TargetType.IMAGE) return;
        if (!commandState.completeIfCurrent("NOTE", event.targetType().name(),
                event.targetId(), event.commandId())) return;
        if (event.targetType() == AuditApplicationRequestedEvent.TargetType.NOTE) {
            if (event.outcome() == AuditApplicationResultEvent.Outcome.REJECTED) {
                noteMapper.updateStatusIfCurrent(event.targetId(), NOTE_PENDING, NOTE_CONVERTED);
            } else if (event.outcome() == AuditApplicationResultEvent.Outcome.CANCEL_REJECTED) {
                noteMapper.updateStatusIfCurrent(event.targetId(), NOTE_CONVERTED, NOTE_PENDING);
            }
        } else {
            if (event.outcome() == AuditApplicationResultEvent.Outcome.REJECTED) {
                tagMapper.updateAuditStatusIfCurrent(event.targetId(), RESOURCE_AUDITING, RESOURCE_WAITING);
            } else if (event.outcome() == AuditApplicationResultEvent.Outcome.CANCEL_REJECTED) {
                tagMapper.updateAuditStatusIfCurrent(event.targetId(), RESOURCE_WAITING, RESOURCE_AUDITING);
            }
        }
    }
}
