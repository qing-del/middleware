package com.jacolp.document.websocket.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.document.config.DocumentProperties;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;

/** 解析文档协议头，同时保持 Yjs 字节内容原样不动。 */
@Component
public class DocumentWsCodec {

    public static final int BINARY_HEADER_BYTES = 18;

    private final ObjectMapper objectMapper;
    private final DocumentProperties properties;

    public DocumentWsCodec(ObjectMapper objectMapper, DocumentProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public DocumentWsBinaryFrame decodeBinary(BinaryMessage message) {
        return decodeBinary(message.getPayload());
    }

    public DocumentWsBinaryFrame decodeBinary(ByteBuffer payload) {
        ByteBuffer source = Objects.requireNonNull(payload, "payload must not be null").asReadOnlyBuffer();
        if (source.remaining() < BINARY_HEADER_BYTES) {
            throw new DocumentWsProtocolException("binary frame is shorter than the protocol header");
        }

        int protocolVersion = Byte.toUnsignedInt(source.get());
        if (protocolVersion != properties.getWebsocket().getProtocolVersion()) {
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: " + protocolVersion);
        }
        DocumentWsFrameType type = DocumentWsFrameType.fromWireValue(Byte.toUnsignedInt(source.get()));
        UUID eventId = new UUID(source.getLong(), source.getLong());
        byte[] rawYjsPayload = new byte[source.remaining()];
        source.get(rawYjsPayload);
        return new DocumentWsBinaryFrame(type, eventId, rawYjsPayload);
    }

    public BinaryMessage encodeBinary(DocumentWsBinaryFrame frame) {
        Objects.requireNonNull(frame, "frame must not be null");
        byte[] rawYjsPayload = frame.payload();
        ByteBuffer wire = ByteBuffer.allocate(BINARY_HEADER_BYTES + rawYjsPayload.length);
        wire.put((byte) properties.getWebsocket().getProtocolVersion());
        wire.put((byte) frame.type().wireValue());
        wire.putLong(frame.eventId().getMostSignificantBits());
        wire.putLong(frame.eventId().getLeastSignificantBits());
        wire.put(rawYjsPayload);
        wire.flip();
        return new BinaryMessage(wire);
    }

    public DocumentWsControlMessage decodeControl(TextMessage message) {
        try {
            DocumentWsControlMessage control = objectMapper.readValue(message.getPayload(), DocumentWsControlMessage.class);
            validateControl(control);
            return control;
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("invalid document WebSocket control JSON", exception);
        }
    }

    public TextMessage encodeControl(DocumentWsControlMessage control) {
        validateControl(control);
        try {
            return new TextMessage(objectMapper.writeValueAsString(control));
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("could not encode document WebSocket control JSON", exception);
        }
    }

    private void validateControl(DocumentWsControlMessage control) {
        if (control == null) {
            throw new DocumentWsProtocolException("control frame must not be null");
        }
        if (control.protocolVersion() != properties.getWebsocket().getProtocolVersion()) {
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: "
                    + control.protocolVersion());
        }
        if (control.type() == null) {
            throw new DocumentWsProtocolException("control frame type is required");
        }
        if (control.requestId() == null) {
            throw new DocumentWsProtocolException("control frame requestId is required");
        }
    }
}
