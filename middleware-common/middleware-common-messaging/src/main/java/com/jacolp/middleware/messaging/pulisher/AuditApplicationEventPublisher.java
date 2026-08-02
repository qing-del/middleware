package com.jacolp.middleware.messaging.pulisher;

import java.util.UUID;

import com.jacolp.middleware.messaging.event.AuditApplicationCancelRequestedEvent;
import com.jacolp.middleware.messaging.event.AuditApplicationRequestedEvent;
import com.jacolp.middleware.messaging.event.AuditApplicationResultEvent;
import com.jacolp.middleware.messaging.constant.EventTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditApplicationEventPublisher {
    private final OutboxEventPublisher outbox;

    public AuditApplicationEventPublisher(OutboxEventPublisher outbox) { this.outbox = outbox; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void request(AuditApplicationRequestedEvent event) {
        publish(EventTypes.AUDIT_APPLICATION_REQUESTED, event.targetType().name(), event.targetId(),
                event.commandId(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(AuditApplicationCancelRequestedEvent event) {
        publish(EventTypes.AUDIT_APPLICATION_CANCEL_REQUESTED, event.targetType().name(), event.targetId(),
                event.commandId(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void result(AuditApplicationResultEvent event) {
        String type = switch (event.outcome()) {
            case ACCEPTED -> EventTypes.AUDIT_APPLICATION_ACCEPTED;
            case REJECTED -> EventTypes.AUDIT_APPLICATION_REJECTED;
            case CANCELLED -> EventTypes.AUDIT_APPLICATION_CANCELLED;
            case CANCEL_REJECTED -> EventTypes.AUDIT_APPLICATION_CANCEL_REJECTED;
        };
        publish(type, event.targetType().name(), event.targetId(), event.commandId(), event);
    }

    private void publish(String type, String aggregateType, long aggregateId,
                         String correlationId, Object payload) {
        outbox.publish(type, type, aggregateType, aggregateId,
                correlationId == null ? UUID.randomUUID().toString() : correlationId, payload);
    }
}
