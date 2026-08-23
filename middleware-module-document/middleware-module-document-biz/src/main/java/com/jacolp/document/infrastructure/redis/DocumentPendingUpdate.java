package com.jacolp.document.infrastructure.redis;

import java.util.Objects;
import java.util.UUID;

/** A pending Yjs update before FLUSH_LOG moves it to MySQL. */
public record DocumentPendingUpdate(
        long documentId,
        byte[] updateData,
        String clientUpdateId,
        Long operatorId,
        String operatorType,
        long createdAt) {

    public DocumentPendingUpdate {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(updateData, "updateData must not be null");
        if (updateData.length == 0) {
            throw new IllegalArgumentException("updateData must not be empty");
        }
        updateData = updateData.clone();
        try {
            UUID.fromString(Objects.requireNonNull(clientUpdateId, "clientUpdateId must not be null"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("clientUpdateId must be a UUID", exception);
        }
        if (operatorType == null || operatorType.isBlank()) {
            throw new IllegalArgumentException("operatorType must not be blank");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt must not be negative");
        }
    }

    @Override
    public byte[] updateData() {
        return updateData.clone();
    }
}
