package com.jacolp.document.infrastructure.redis;

import java.util.Objects;

/** A pending update together with the Redis Stream entry ID required for cutoff and XDEL. */
public record StoredDocumentPendingUpdate(String redisOpId, DocumentPendingUpdate update) {

    public StoredDocumentPendingUpdate {
        if (redisOpId == null || redisOpId.isBlank()) {
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        Objects.requireNonNull(update, "update must not be null");
    }
}
