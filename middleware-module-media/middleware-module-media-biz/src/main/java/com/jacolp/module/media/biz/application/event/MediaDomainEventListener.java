package com.jacolp.module.media.biz.application.event;

import com.jacolp.middleware.messaging.AuditReviewedEvent;
import com.jacolp.middleware.messaging.AuditApplicationResultEvent;
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
public class MediaDomainEventListener {
    private final EventMessageCodec codec;
    private final InboxService inboxService;
    private final EventRetryPublisher retryPublisher;
    private final MediaAuditReviewedEventHandler auditReviewedHandler;
    private final MediaAuditApplicationResultHandler applicationResultHandler;

    public MediaDomainEventListener(EventMessageCodec codec, InboxService inboxService,
            EventRetryPublisher retryPublisher, MediaAuditReviewedEventHandler auditReviewedHandler,
            MediaAuditApplicationResultHandler applicationResultHandler) {
        this.codec = codec;
        this.inboxService = inboxService;
        this.retryPublisher = retryPublisher;
        this.auditReviewedHandler = auditReviewedHandler;
        this.applicationResultHandler = applicationResultHandler;
    }

    @RabbitListener(queues = EventTopology.MEDIA_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (EventTypes.AUDIT_REVIEWED.equals(envelope.eventType())) {
                inboxService.consume(envelope, MediaAuditReviewedEventHandler.CONSUMER_NAME,
                        ignored -> auditReviewedHandler.apply(codec.payloadItems(envelope, AuditReviewedEvent.class)));
            } else if (isApplicationResult(envelope.eventType())) {
                inboxService.consume(envelope, "media.audit-application-result",
                        ignored -> applicationResultHandler.apply(
                                codec.payload(envelope, AuditApplicationResultEvent.class)));
            } else throw new IllegalArgumentException("Unsupported media event type: " + envelope.eventType());
        } catch (RuntimeException failure) {
            retryPublisher.retryOrDeadLetter(EventTopology.MEDIA_QUEUE, message, failure);
        }
    }

    private static boolean isApplicationResult(String type) {
        return EventTypes.AUDIT_APPLICATION_ACCEPTED.equals(type)
                || EventTypes.AUDIT_APPLICATION_REJECTED.equals(type)
                || EventTypes.AUDIT_APPLICATION_CANCELLED.equals(type)
                || EventTypes.AUDIT_APPLICATION_CANCEL_REJECTED.equals(type);
    }
}
