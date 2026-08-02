package com.jacolp.module.audit.biz.application.event;

import com.jacolp.middleware.messaging.event.AuditApplicationCancelRequestedEvent;
import com.jacolp.middleware.messaging.event.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.base.EventEnvelope;
import com.jacolp.middleware.messaging.tools.EventMessageCodec;
import com.jacolp.middleware.messaging.pulisher.EventRetryPublisher;
import com.jacolp.middleware.messaging.constant.EventTopology;
import com.jacolp.middleware.messaging.constant.EventTypes;
import com.jacolp.middleware.messaging.service.InboxService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditCommandEventListener {
    private final EventMessageCodec codec;
    private final InboxService inbox;
    private final EventRetryPublisher retries;
    private final AuditApplicationCommandHandler handler;

    public AuditCommandEventListener(EventMessageCodec codec, InboxService inbox,
            EventRetryPublisher retries, AuditApplicationCommandHandler handler) {
        this.codec = codec; this.inbox = inbox; this.retries = retries; this.handler = handler;
    }

    @RabbitListener(queues = EventTopology.AUDIT_QUEUE)
    public void onMessage(Message message) {
        try {
            EventEnvelope envelope = codec.decode(message);
            inbox.consume(envelope, AuditApplicationCommandHandler.CONSUMER_NAME, ignored -> {
                if (EventTypes.AUDIT_APPLICATION_REQUESTED.equals(envelope.eventType())) {
                    handler.create(codec.payload(envelope, AuditApplicationRequestedEvent.class));
                } else if (EventTypes.AUDIT_APPLICATION_CANCEL_REQUESTED.equals(envelope.eventType())) {
                    handler.cancel(codec.payload(envelope, AuditApplicationCancelRequestedEvent.class));
                } else {
                    throw new IllegalArgumentException("Unsupported audit command: " + envelope.eventType());
                }
            });
        } catch (RuntimeException failure) {
            retries.retryOrDeadLetter(EventTopology.AUDIT_QUEUE, message, failure);
        }
    }
}
