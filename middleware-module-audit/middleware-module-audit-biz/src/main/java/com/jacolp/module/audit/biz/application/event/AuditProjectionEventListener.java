package com.jacolp.module.audit.biz.application.event;

import com.jacolp.middleware.messaging.EventEnvelope;
import com.jacolp.middleware.messaging.EventMessageCodec;
import com.jacolp.middleware.messaging.EventRetryPublisher;
import com.jacolp.middleware.messaging.EventTopology;
import com.jacolp.middleware.messaging.EventTypes;
import com.jacolp.middleware.messaging.InboxService;
import com.jacolp.middleware.messaging.UserProfileChangedEvent;
import com.jacolp.module.audit.biz.infrastructure.persistence.mapper.AuditQueryProjectionMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditProjectionEventListener {
    private final EventMessageCodec codec;
    private final InboxService inbox;
    private final EventRetryPublisher retries;
    private final AuditQueryProjectionMapper projections;

    public AuditProjectionEventListener(EventMessageCodec codec, InboxService inbox,
            EventRetryPublisher retries, AuditQueryProjectionMapper projections) {
        this.codec = codec; this.inbox = inbox; this.retries = retries; this.projections = projections;
    }

    @RabbitListener(queues = EventTopology.AUDIT_PROJECTION_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            if (!EventTypes.USER_PROFILE_CHANGED.equals(envelope.eventType())) {
                throw new IllegalArgumentException("Unsupported audit projection event: " + envelope.eventType());
            }
            UserProfileChangedEvent event = codec.payload(envelope, UserProfileChangedEvent.class);
            inbox.consume(envelope, "audit.user-profile-projection", ignored ->
                    projections.upsertUser(event.userId(), event.username(), event.nickname()));
        } catch (RuntimeException failure) {
            retries.retryOrDeadLetter(EventTopology.AUDIT_PROJECTION_QUEUE, message, failure);
        }
    }
}
