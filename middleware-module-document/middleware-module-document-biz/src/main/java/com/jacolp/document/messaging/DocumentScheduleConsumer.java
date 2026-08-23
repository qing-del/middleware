package com.jacolp.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consumes document scheduling signals; each handler rechecks durable state instead of trusting message uniqueness. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentScheduleConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentScheduleConsumer.class);

    private final ObjectMapper objectMapper;
    private final DocumentFlushLogService flushLogService;
    private final EventRetryPublisher retryPublisher;

    public DocumentScheduleConsumer(ObjectMapper objectMapper, DocumentFlushLogService flushLogService,
                                    EventRetryPublisher retryPublisher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.retryPublisher = Objects.requireNonNull(retryPublisher, "retryPublisher must not be null");
    }

    @RabbitListener(queues = DocumentScheduleTopology.QUEUE)
    public void onMessage(Message message) {
        try {
            DocumentScheduleMessage schedule = objectMapper.readValue(message.getBody(), DocumentScheduleMessage.class);
            if (schedule.type() != DocumentScheduleType.FLUSH_LOG) {
                throw new IllegalArgumentException("unsupported document schedule type: " + schedule.type());
            }
            flushLogService.flush(schedule.documentId());
        } catch (Exception exception) {
            RuntimeException failure = exception instanceof RuntimeException runtime ? runtime
                    : new IllegalArgumentException("invalid document scheduling signal", exception);
            boolean retrying = retryPublisher.retryOrDeadLetter(DocumentScheduleTopology.QUEUE, message, failure);
            log.warn("Document scheduling signal failed; retrying={}: {}", retrying, failure.getMessage());
        }
    }
}
