package com.jacolp.document.websocket.protocol;

import java.util.Arrays;

/** Binary outer-header frame types; their payload is raw Yjs data. */
public enum DocumentWsFrameType {
    CLIENT_UPDATE(0x01),
    CRDT_UPDATE(0x02),
    SNAPSHOT_STATE(0x03),
    BOOTSTRAP_UPDATE(0x04),
    AWARENESS(0x05);

    private final int wireValue;

    DocumentWsFrameType(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static DocumentWsFrameType fromWireValue(int wireValue) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue == wireValue)
                .findFirst()
                .orElseThrow(() -> new DocumentWsProtocolException("unknown document WebSocket frame type: " + wireValue));
    }
}
