package com.jacolp.document.messaging;

import com.jacolp.document.api.model.DocumentScheduleType;
import java.util.Objects;

/** A small, content-free signal that asks a consumer to recheck document state. */
public record DocumentScheduleMessage(
        Long documentId,
        DocumentScheduleType type,
        Long triggerTime,
        String closeToken) {

    public DocumentScheduleMessage {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (triggerTime == null || triggerTime < 0) {
            throw new IllegalArgumentException("triggerTime must be non-negative");
        }
        if (type == DocumentScheduleType.CLOSE && (closeToken == null || closeToken.isBlank())) {
            throw new IllegalArgumentException("closeToken is required for CLOSE");
        }
    }
}
