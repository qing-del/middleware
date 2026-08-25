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

    /** 创建使用统一 JSON 序列化器和协议版本配置的 codec。 */
    public DocumentWsCodec(ObjectMapper objectMapper, DocumentProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 从 WebSocket 二进制消息中解码协议头和原始 payload。 */
    public DocumentWsBinaryFrame decodeBinary(BinaryMessage message) {
        return decodeBinary(message.getPayload());
    }

    /** 校验版本、帧类型和 UUID 后读取剩余的原始 Yjs/awareness 字节。 */
    public DocumentWsBinaryFrame decodeBinary(ByteBuffer payload) {
        ByteBuffer source = Objects.requireNonNull(payload, "payload must not be null").asReadOnlyBuffer();
        if (source.remaining() < BINARY_HEADER_BYTES) {
            throw new DocumentWsProtocolException("binary frame is shorter than the protocol header");
        }

        int protocolVersion = Byte.toUnsignedInt(source.get());
        if (protocolVersion != properties.getWebsocket().getProtocolVersion()) {
            throw new DocumentWsProtocolException("unsupported document WebSocket protocol version: " + protocolVersion);
        }
        // 只有固定长度的外层头由 Java 解析；剩余字节保持透明，交给客户端 Yjs 绑定处理。
        DocumentWsFrameType type = DocumentWsFrameType.fromWireValue(Byte.toUnsignedInt(source.get()));
        UUID eventId = new UUID(source.getLong(), source.getLong());
        byte[] rawYjsPayload = new byte[source.remaining()];
        source.get(rawYjsPayload);
        return new DocumentWsBinaryFrame(type, eventId, rawYjsPayload);
    }

    /** 将帧类型、事件 UUID 和原始 payload 编码为固定格式二进制消息。 */
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

    /** 解析并校验 JSON 控制帧。 */
    public DocumentWsControlMessage decodeControl(TextMessage message) {
        try {
            DocumentWsControlMessage control = objectMapper.readValue(message.getPayload(), DocumentWsControlMessage.class);
            validateControl(control);
            return control;
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("invalid document WebSocket control JSON", exception);
        }
    }

    /** 校验控制帧后编码为 JSON 文本消息。 */
    public TextMessage encodeControl(DocumentWsControlMessage control) {
        validateControl(control);
        try {
            return new TextMessage(objectMapper.writeValueAsString(control));
        } catch (JsonProcessingException exception) {
            throw new DocumentWsProtocolException("could not encode document WebSocket control JSON", exception);
        }
    }

    /** 统一校验控制帧版本、类型和请求关联 ID。 */
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
