package com.jacolp.middleware.messaging;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileEventPublisher {
    private final OutboxEventPublisher outbox;
    public UserProfileEventPublisher(OutboxEventPublisher outbox) { this.outbox = outbox; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UserProfileChangedEvent event) {
        outbox.publish(EventTypes.USER_PROFILE_CHANGED, EventTypes.USER_PROFILE_CHANGED,
                "USER", event.userId(), null, event);
    }
}
