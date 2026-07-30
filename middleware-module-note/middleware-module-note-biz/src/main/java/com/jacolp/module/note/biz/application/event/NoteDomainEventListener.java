package com.jacolp.module.note.biz.application.event;

import com.jacolp.middleware.messaging.AuditReviewedEvent;
import com.jacolp.middleware.messaging.EventEnvelope;
import com.jacolp.middleware.messaging.EventMessageCodec;
import com.jacolp.middleware.messaging.EventRetryPublisher;
import com.jacolp.middleware.messaging.EventTopology;
import com.jacolp.middleware.messaging.EventTypes;
import com.jacolp.middleware.messaging.InboxService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NoteDomainEventListener {
    private final EventMessageCodec codec;
    private final InboxService inboxService;
    private final EventRetryPublisher retryPublisher;
    private final NoteAuditReviewedEventHandler auditReviewedHandler;

    public NoteDomainEventListener(EventMessageCodec codec, InboxService inboxService,
            EventRetryPublisher retryPublisher, NoteAuditReviewedEventHandler auditReviewedHandler) {
        this.codec = codec;
        this.inboxService = inboxService;
        this.retryPublisher = retryPublisher;
        this.auditReviewedHandler = auditReviewedHandler;
    }

    @RabbitListener(queues = EventTopology.NOTE_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.AUDIT_REVIEWED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported note event type: " + envelope.eventType());
            }
            inboxService.consume(envelope, NoteAuditReviewedEventHandler.CONSUMER_NAME,
                    ignored -> auditReviewedHandler.apply(codec.payloadItems(envelope, AuditReviewedEvent.class)));
        } catch (RuntimeException failure) {
            retryPublisher.retryOrDeadLetter(EventTopology.NOTE_QUEUE, message, failure);
        }
    }
}
