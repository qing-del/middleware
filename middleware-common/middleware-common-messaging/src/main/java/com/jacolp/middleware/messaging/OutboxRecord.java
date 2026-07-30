package com.jacolp.middleware.messaging;

import java.time.LocalDateTime;

public record OutboxRecord(
        long id,
        String eventId,
        String eventType,
        String routingKey,
        String payload,
        int retryCount,
        LocalDateTime createTime) {
}
