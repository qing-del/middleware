package com.jacolp.middleware.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaResourceDeleteEventPublisher {
    private final OutboxEventPublisher outboxEventPublisher;

    public MediaResourceDeleteEventPublisher(OutboxEventPublisher outboxEventPublisher) {
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(List<MediaResourceDeleteRequestedEvent> events) {
        if (events == null || events.isEmpty()) return;
        outboxEventPublisher.publishPartitioned(EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED,
                EventTypes.MEDIA_RESOURCE_DELETE_REQUESTED, "MEDIA_RESOURCE",
                events.getFirst().resourceId(), UUID.randomUUID().toString(), events);
    }
}
