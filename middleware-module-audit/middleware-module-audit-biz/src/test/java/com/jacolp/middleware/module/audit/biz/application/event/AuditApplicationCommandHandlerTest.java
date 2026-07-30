package com.jacolp.middleware.module.audit.biz.application.event;

import com.jacolp.middleware.messaging.AuditApplicationEventPublisher;
import com.jacolp.middleware.messaging.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.AuditApplicationResultEvent;
import com.jacolp.module.audit.api.AuditApplicationResult;
import com.jacolp.module.audit.api.AuditTargetType;
import com.jacolp.module.audit.biz.application.api.AuditApplicationApiService;
import com.jacolp.module.audit.biz.application.event.AuditApplicationCommandHandler;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditApplicationCommandHandlerTest {

    @Test
    void createsAuditOwnedRecordAndPublishesAcceptedResult() {
        AuditApplicationApiService applications = mock(AuditApplicationApiService.class);
        AuditApplicationEventPublisher events = mock(AuditApplicationEventPublisher.class);
        AuditApplicationRequestedEvent command = new AuditApplicationRequestedEvent(
                "command-1", AuditApplicationRequestedEvent.TargetType.NOTE, 7L, 9L, null,
                "A note", null);
        when(applications.createApplication(any())).thenReturn(
                new AuditApplicationResult(19L, AuditTargetType.NOTE, 7L, 9L));

        new AuditApplicationCommandHandler(applications, events,
                mock(AuditQueryProjectionMapper.class)).create(command);

        ArgumentCaptor<AuditApplicationResultEvent> result =
                ArgumentCaptor.forClass(AuditApplicationResultEvent.class);
        verify(events).result(result.capture());
        assertThat(result.getValue().outcome()).isEqualTo(AuditApplicationResultEvent.Outcome.ACCEPTED);
        assertThat(result.getValue().auditApplicationId()).isEqualTo(19L);
    }
}
