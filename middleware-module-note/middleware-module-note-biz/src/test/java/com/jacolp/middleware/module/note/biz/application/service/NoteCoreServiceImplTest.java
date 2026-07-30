package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jacolp.module.note.biz.application.service.NoteCoreServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.NoteStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.messaging.AsyncCommandStateService;
import com.jacolp.middleware.messaging.AuditApplicationCancelRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationEventPublisher;
import com.jacolp.middleware.messaging.AuditApplicationRequestedEvent;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteMapper;

class NoteCoreServiceImplTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void submitAuditCreatesNoteApplicationAndMovesConvertedNoteToPending() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationEventPublisher auditApi = mock(AuditApplicationEventPublisher.class);
        AsyncCommandStateService state = mock(AsyncCommandStateService.class);
        BaseContext.setCurrentId(9L);
        PermissionContext.setAdmin(false);
        NoteDO note = note(7L, 9L, NoteStatus.CONVERTED);
        when(mapper.selectById(7L)).thenReturn(note);
        when(state.tryBegin(any(), any(), any(Long.class), any(), any())).thenReturn(true);
        when(mapper.updateNote(note)).thenReturn(1);

        service(mapper, auditApi, state).submitNoteAudit(7L);

        ArgumentCaptor<AuditApplicationRequestedEvent> command = ArgumentCaptor.forClass(AuditApplicationRequestedEvent.class);
        verify(auditApi).request(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationRequestedEvent.TargetType.NOTE, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(7L, command.getValue().targetId());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().applicantUserId());
        org.junit.jupiter.api.Assertions.assertEquals("note-7", command.getValue().targetName());
        verify(mapper).updateNote(note);
        org.junit.jupiter.api.Assertions.assertEquals(NoteStatus.PENDING_AUDIT.getCode(), note.getStatus());
    }

    @Test
    void submitAuditRejectsApprovedStatusBeforeCallingAuditApi() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationEventPublisher auditApi = mock(AuditApplicationEventPublisher.class);
        BaseContext.setCurrentId(9L);
        PermissionContext.setAdmin(false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 9L, NoteStatus.APPROVED));

        assertThrows(BaseException.class, () -> service(mapper, auditApi, mock(AsyncCommandStateService.class)).submitNoteAudit(7L));

        verifyNoInteractions(auditApi);
        verify(mapper, never()).updateNote(any());
    }

    @Test
    void getByIdRejectsAnotherUsersPrivateNote() {
        NoteMapper mapper = mock(NoteMapper.class);
        BaseContext.setCurrentId(9L);
        PermissionContext.setAdmin(false);
        when(mapper.selectById(7L)).thenReturn(note(7L, 10L, NoteStatus.CONVERTED));

        assertThrows(BaseException.class, () -> service(mapper, mock(AuditApplicationEventPublisher.class),
                mock(AsyncCommandStateService.class)).getById(7L));
    }

    @Test
    void cancelAuditUsesNoteApplicantAndMovesPendingNoteBackToConverted() {
        NoteMapper mapper = mock(NoteMapper.class);
        AuditApplicationEventPublisher auditApi = mock(AuditApplicationEventPublisher.class);
        AsyncCommandStateService state = mock(AsyncCommandStateService.class);
        BaseContext.setCurrentId(9L);
        PermissionContext.setAdmin(false);
        NoteDO note = note(7L, 9L, NoteStatus.PENDING_AUDIT);
        when(mapper.selectById(7L)).thenReturn(note);
        when(mapper.updateNote(note)).thenReturn(1);
        when(state.tryBegin(any(), any(), any(Long.class), any(), any())).thenReturn(true);

        service(mapper, auditApi, state).cancelNoteAudit(7L);

        ArgumentCaptor<AuditApplicationCancelRequestedEvent> command = ArgumentCaptor.forClass(AuditApplicationCancelRequestedEvent.class);
        verify(auditApi).cancel(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationRequestedEvent.TargetType.NOTE, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(7L, command.getValue().targetId());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().actorUserId());
        org.junit.jupiter.api.Assertions.assertEquals(NoteStatus.CONVERTED.getCode(), note.getStatus());
        verify(mapper).updateNote(note);
    }

    private static NoteCoreServiceImpl service(NoteMapper mapper, AuditApplicationEventPublisher auditApi,
                                               AsyncCommandStateService state) {
        NoteCoreServiceImpl service = new NoteCoreServiceImpl();
        ReflectionTestUtils.setField(service, "noteMapper", mapper);
        ReflectionTestUtils.setField(service, "auditEvents", auditApi);
        ReflectionTestUtils.setField(service, "commandState", state);
        return service;
    }

    private static NoteDO note(Long id, Long userId, NoteStatus status) {
        NoteDO note = new NoteDO();
        note.setId(id);
        note.setUserId(userId);
        note.setTitle("note-" + id);
        note.setStatus(status.getCode());
        return note;
    }
}
