package com.jacolp.document.infrastructure.redis;

import java.util.Objects;

/** 待持久化更新及其 Redis Stream 条目 ID，后者用于确定截断范围和执行 XDEL。 */
public record StoredDocumentPendingUpdate(String redisOpId, DocumentPendingUpdate update) {

    public StoredDocumentPendingUpdate {
        if (redisOpId == null || redisOpId.isBlank()) {
            throw new IllegalArgumentException("redisOpId must not be blank");
        }
        Objects.requireNonNull(update, "update must not be null");
    }
}
