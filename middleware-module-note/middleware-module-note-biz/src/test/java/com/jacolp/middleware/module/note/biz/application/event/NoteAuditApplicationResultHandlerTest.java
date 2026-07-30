package com.jacolp.middleware.module.note.biz.application.event;

import com.jacolp.middleware.messaging.AsyncCommandStateService;
import com.jacolp.middleware.messaging.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationResultEvent;
import com.jacolp.module.note.biz.application.event.NoteAuditApplicationResultHandler;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteAuditApplicationResultHandlerTest {

    @Test
    void correlatedCreateRejectionRollsBackTheOptimisticNoteStatus() {
        AsyncCommandStateService state = mock(AsyncCommandStateService.class);
        NoteMapper notes = mock(NoteMapper.class);
        TagMapper tags = mock(TagMapper.class);
        when(state.completeIfCurrent("NOTE", "NOTE", 7L, "command-1")).thenReturn(true);
        AuditApplicationResultEvent result = new AuditApplicationResultEvent("command-1",
                AuditApplicationRequestedEvent.TargetType.NOTE, 7L,
                AuditApplicationResultEvent.Outcome.REJECTED, null, "ALREADY_PENDING");

        new NoteAuditApplicationResultHandler(state, notes, tags).apply(result);

        verify(notes).updateStatusIfCurrent(7L, (short) 4, (short) 2);
    }
}
