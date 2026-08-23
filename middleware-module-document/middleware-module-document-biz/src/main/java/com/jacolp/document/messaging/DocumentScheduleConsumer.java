package com.jacolp.document.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.common.messaging.pulisher.EventRetryPublisher;
import com.jacolp.document.api.model.DocumentScheduleType;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.close.DocumentCloseService;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.config.DocumentProperties;
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
    private final DocumentCompactService compactService;
    private final DocumentCloseService closeService;
    private final DocumentSchedulePublisher schedulePublisher;
    private final DocumentProperties documentProperties;
    private final EventRetryPublisher retryPublisher;

    public DocumentScheduleConsumer(ObjectMapper objectMapper, DocumentFlushLogService flushLogService,
                                    DocumentCompactService compactService, DocumentCloseService closeService,
                                    DocumentSchedulePublisher schedulePublisher, DocumentProperties documentProperties,
                                    EventRetryPublisher retryPublisher) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.compactService = Objects.requireNonNull(compactService, "compactService must not be null");
        this.closeService = Objects.requireNonNull(closeService, "closeService must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
        this.retryPublisher = Objects.requireNonNull(retryPublisher, "retryPublisher must not be null");
    }

    @RabbitListener(queues = DocumentScheduleTopology.QUEUE)
    public void onMessage(Message message) {
        try {
            DocumentScheduleMessage schedule = objectMapper.readValue(message.getBody(), DocumentScheduleMessage.class);
            if (schedule.type() == DocumentScheduleType.FLUSH_LOG) {
                DocumentFlushLogResult result = flushLogService.flush(schedule.documentId());
                if (result.processedCount() >= documentProperties.getCompact().getMaxUnmergedOps()
                        || result.processedBytes() >= documentProperties.getCompact().getMaxUnmergedBytes()) {
                    schedulePublisher.scheduleCompactImmediately(schedule.documentId());
                } else {
                    schedulePublisher.scheduleCompact(schedule.documentId());
                }
            } else if (schedule.type() == DocumentScheduleType.COMPACT) {
                compactService.compact(schedule.documentId());
            } else if (schedule.type() == DocumentScheduleType.CLOSE) {
                closeService.close(schedule.documentId(), schedule.closeToken());
            } else {
                throw new IllegalArgumentException("unsupported document schedule type: " + schedule.type());
            }
        } catch (Exception exception) {
            RuntimeException failure = exception instanceof RuntimeException runtime ? runtime
                    : new IllegalArgumentException("invalid document scheduling signal", exception);
            boolean retrying = retryPublisher.retryOrDeadLetter(DocumentScheduleTopology.QUEUE, message, failure);
            log.warn("Document scheduling signal failed; retrying={}: {}", retrying, failure.getMessage());
        }
    }
}
