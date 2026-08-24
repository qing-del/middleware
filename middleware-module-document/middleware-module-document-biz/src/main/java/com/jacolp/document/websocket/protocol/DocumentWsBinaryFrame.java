package com.jacolp.document.websocket.protocol;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** 二进制帧外层结构；其中 payload 对 Java 始终是未解析的 Yjs 字节。 */
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
