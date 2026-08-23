package com.jacolp.document.websocket.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Outer binary frame only; payload remains opaque Yjs bytes to Java. */
public record DocumentWsBinaryFrame(DocumentWsFrameType type, UUID eventId, byte[] payload) {

    public DocumentWsBinaryFrame {
        type = Objects.requireNonNull(type, "type must not be null");
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
