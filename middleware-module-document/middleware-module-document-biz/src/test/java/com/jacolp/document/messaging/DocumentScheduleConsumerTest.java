package com.jacolp.document.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

class DocumentScheduleConsumerTest {

    @Test
    void flushesStateRecheckedFromRedisForFlushSchedule() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        DocumentFlushLogService flushLogService = mock(DocumentFlushLogService.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(objectMapper, flushLogService, retryPublisher);
        Message message = new Message(objectMapper.writeValueAsBytes(new DocumentScheduleMessage(7L,
                DocumentScheduleType.FLUSH_LOG, 1_000L, null)));

        consumer.onMessage(message);

        verify(flushLogService).flush(7L);
    }

    @Test
    void sendsFailuresThroughExistingRetryAndDeadLetterMechanism() {
        DocumentFlushLogService flushLogService = mock(DocumentFlushLogService.class);
        EventRetryPublisher retryPublisher = mock(EventRetryPublisher.class);
        when(retryPublisher.retryOrDeadLetter(any(), any(), any())).thenReturn(true);
        DocumentScheduleConsumer consumer = new DocumentScheduleConsumer(new ObjectMapper(), flushLogService, retryPublisher);

        consumer.onMessage(new Message(new byte[] {'b', 'a', 'd'}));

        verify(retryPublisher).retryOrDeadLetter(org.mockito.Mockito.eq(DocumentScheduleTopology.QUEUE), any(), any());
    }
}
