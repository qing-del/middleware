package com.jacolp.middleware.module.note.biz.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.enums.AuditStatus;
import com.jacolp.exception.BaseException;
import com.jacolp.middleware.messaging.AsyncCommandStateService;
import com.jacolp.middleware.messaging.AuditApplicationCancelRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationEventPublisher;
import com.jacolp.middleware.messaging.AuditApplicationRequestedEvent;
import com.jacolp.module.note.biz.application.service.TagServiceImpl;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.TagDO;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class TagServiceImplTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void submitAuditUsesTagApplicantAndMovesTagToAuditingAfterApplicationCreation() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationEventPublisher auditApi = org.mockito.Mockito.mock(AuditApplicationEventPublisher.class);
        AsyncCommandStateService state = org.mockito.Mockito.mock(AsyncCommandStateService.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));
        when(state.tryBegin(any(), any(), any(Long.class), any(), any())).thenReturn(true);

        service(mapper, auditApi, state).submitTagAudit(7L);

        ArgumentCaptor<AuditApplicationRequestedEvent> command = ArgumentCaptor.forClass(AuditApplicationRequestedEvent.class);
        verify(auditApi).request(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationRequestedEvent.TargetType.TAG, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(7L, command.getValue().targetId());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().applicantUserId());
        org.junit.jupiter.api.Assertions.assertEquals("tag-7", command.getValue().targetName());
        verify(mapper).updateAuditStatusByIds(List.of(7L), AuditStatus.AUDITING.getCode());
    }

    @Test
    void submitAuditRejectsExistingPendingApplicationBeforeChangingTagStatus() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationEventPublisher auditApi = org.mockito.Mockito.mock(AuditApplicationEventPublisher.class);
        AsyncCommandStateService state = org.mockito.Mockito.mock(AsyncCommandStateService.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.WAIT));

        assertThrows(BaseException.class, () -> service(mapper, auditApi, state).submitTagAudit(7L));

        verify(auditApi, never()).request(any());
        verify(mapper, never()).updateAuditStatusByIds(any(), any());
    }

    @Test
    void cancelAuditUsesCurrentUserAndMovesTagBackToWaiting() {
        TagMapper mapper = org.mockito.Mockito.mock(TagMapper.class);
        AuditApplicationEventPublisher auditApi = org.mockito.Mockito.mock(AuditApplicationEventPublisher.class);
        AsyncCommandStateService state = org.mockito.Mockito.mock(AsyncCommandStateService.class);
        BaseContext.setCurrentId(9L);
        when(mapper.selectByIdAndUserId(7L, 9L)).thenReturn(tag(7L, 9L, AuditStatus.AUDITING));
        when(state.tryBegin(any(), any(), any(Long.class), any(), any())).thenReturn(true);

        service(mapper, auditApi, state).cancelTagAudit(7L);

        ArgumentCaptor<AuditApplicationCancelRequestedEvent> command = ArgumentCaptor.forClass(AuditApplicationCancelRequestedEvent.class);
        verify(auditApi).cancel(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(AuditApplicationRequestedEvent.TargetType.TAG, command.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals(9L, command.getValue().actorUserId());
        verify(mapper).updateAuditStatusByIds(List.of(7L), AuditStatus.WAIT.getCode());
    }

    private static TagServiceImpl service(TagMapper mapper, AuditApplicationEventPublisher auditApi,
                                          AsyncCommandStateService state) {
        TagServiceImpl service = new TagServiceImpl();
        ReflectionTestUtils.setField(service, "tagMapper", mapper);
        ReflectionTestUtils.setField(service, "auditEvents", auditApi);
        ReflectionTestUtils.setField(service, "commandState", state);
        return service;
    }

    private static TagDO tag(Long id, Long userId, AuditStatus status) {
        TagDO tag = new TagDO();
        tag.setId(id);
        tag.setUserId(userId);
        tag.setTagName("tag-" + id);
        tag.setAuditStatus(status.getCode());
        return tag;
    }
}
