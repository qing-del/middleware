package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsControlMessage;
import com.jacolp.document.websocket.protocol.DocumentWsControlType;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.document.websocket.protocol.DocumentWsProtocolException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Authenticated document collaboration endpoint. Java routes opaque Yjs bytes but never parses them. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketHandler extends AbstractWebSocketHandler {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentWsCodec codec;
    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentRoomManager roomManager;
    private final DocumentBootstrapService bootstrapService;
    private final DocumentProperties properties;
    private final ConcurrentHashMap<String, Long> joinedDocumentIds = new ConcurrentHashMap<>();

    public DocumentWebSocketHandler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                    DocumentRedisRepository documentRedisRepository, DocumentRoomManager roomManager,
                                    DocumentBootstrapService bootstrapService, DocumentProperties properties) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.bootstrapService = Objects.requireNonNull(bootstrapService, "bootstrapService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DocumentWsControlMessage control;
        try {
            control = codec.decodeControl(message);
        } catch (DocumentWsProtocolException exception) {
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            switch (control.type()) {
                case JOIN_DOCUMENT -> handleJoin(session, control);
                case LEAVE_DOCUMENT -> leaveSession(session);
                case PING -> sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.PONG,
                        control.requestId(), null, null, null, null, null));
                default -> sendError(session, control.requestId(), "DOCUMENT_PROTOCOL_ERROR",
                        "unsupported client control frame type: " + control.type());
            }
        } catch (DocumentRoomLimitExceededException exception) {
            sendError(session, control.requestId(), "DOCUMENT_ROOM_LIMIT_EXCEEDED", exception.getMessage());
        } catch (DocumentRoomAccessException exception) {
            sendError(session, control.requestId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (DocumentBootstrapException exception) {
            leaveSession(session);
            sendError(session, control.requestId(), "DOCUMENT_SYNC_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(session, control.requestId(), "DOCUMENT_PROTOCOL_ERROR", "document request could not be completed");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DocumentWsBinaryFrame frame;
        try {
            frame = codec.decodeBinary(message);
        } catch (DocumentWsProtocolException exception) {
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            if (frame.type() == DocumentWsFrameType.CLIENT_UPDATE) {
                acceptClientUpdate(session, frame);
            } else if (frame.type() == DocumentWsFrameType.AWARENESS) {
                broadcastAwareness(session, frame);
            } else {
                sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", "binary frame type is not accepted from clients");
            }
        } catch (DocumentRoomAccessException exception) {
            sendError(session, frame.eventId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_ACCEPT_FAILED", "document update could not be accepted");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leaveSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        leaveSession(session);
    }

    private void handleJoin(WebSocketSession session, DocumentWsControlMessage control) {
        long documentId = requireDocumentId(control.documentId());
        Long alreadyJoinedDocumentId = joinedDocumentIds.get(session.getId());
        if (alreadyJoinedDocumentId != null) {
            if (alreadyJoinedDocumentId != documentId) {
                throw new DocumentRoomAccessException("a WebSocket session may join only one document at a time");
            }
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null));
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
            return;
        }

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        DocumentDO document = documentMapper.selectActiveByIdAndTeamId(documentId, principal.userId());
        if (document == null) {
            sendError(session, control.requestId(), "DOCUMENT_NOT_FOUND", "document does not exist or is not accessible");
            return;
        }

        DocumentRoom room = roomManager.getOrCreate(documentId, principal.userId());
        room.join(session, principal);
        joinedDocumentIds.put(session.getId(), documentId);
        try {
            updateRoomMeta(document, principal.userId());
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null));
            bootstrapService.sendBootstrap(document, room.requireSession(session.getId()).session());
            room.markActive(session.getId());
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
        } catch (RuntimeException exception) {
            leaveSession(session);
            throw exception;
        }
    }

    private void acceptClientUpdate(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        if (frame.payload().length > properties.getWebsocket().getMaxUpdateBytes()) {
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_TOO_LARGE", "Yjs update exceeds configured maximum size");
            return;
        }
        if (frame.payload().length == 0) {
            sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", "Yjs update must not be empty");
            return;
        }

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        long now = System.currentTimeMillis();
        String redisOpId = documentRedisRepository.appendPendingUpdate(new DocumentPendingUpdate(room.documentId(),
                frame.payload(), frame.eventId().toString(), principal.userId(), principal.clientId(), now));

        int modified = documentMapper.updateLastModificationIfActive(room.documentId(), principal.userId(),
                LocalDateTime.now(APPLICATION_ZONE), principal.userId());
        if (modified != 1) {
            throw new DocumentRoomAccessException("document no longer accepts updates in this personal scope");
        }
        saveRoomMeta(room.documentId(), principal.userId(), now);
        sendControl(room.requireSession(session.getId()).session(), new DocumentWsControlMessage(protocolVersion(),
                DocumentWsControlType.UPDATE_ACCEPTED, frame.eventId(), room.documentId(), frame.eventId(), redisOpId, null, null));
        room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.CRDT_UPDATE,
                frame.eventId(), frame.payload())), session.getId());
    }

    private void broadcastAwareness(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                frame.eventId(), frame.payload())), session.getId());
    }

    private DocumentRoom requireActiveRoom(WebSocketSession session) {
        Long documentId = joinedDocumentIds.get(session.getId());
        if (documentId == null) {
            throw new DocumentRoomAccessException("WebSocket session has not joined a document");
        }
        DocumentRoom room = roomManager.find(documentId)
                .orElseThrow(() -> new DocumentRoomAccessException("document Room is not available"));
        if (room.requireSession(session.getId()).syncStatus() != DocumentSessionSyncStatus.ACTIVE) {
            throw new DocumentRoomAccessException("document session is still synchronizing");
        }
        return room;
    }

    private void updateRoomMeta(DocumentDO document, long userId) {
        long lastModifiedAt = document.getLastModifyTime() == null
                ? System.currentTimeMillis()
                : document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli();
        saveRoomMeta(document.getId(), userId, lastModifiedAt);
    }

    private void saveRoomMeta(long documentId, long userId, long lastModifiedAt) {
        Optional<DocumentRoomMeta> existing = documentRedisRepository.findRoomMeta(documentId);
        if (existing.isPresent() && existing.get().teamId() != userId) {
            throw new DocumentRoomAccessException("Redis room meta does not match authenticated personal scope");
        }
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(documentId, userId, false, null, lastModifiedAt, userId));
    }

    private void leaveSession(WebSocketSession session) {
        Long documentId = joinedDocumentIds.remove(session.getId());
        if (documentId != null) {
            roomManager.find(documentId).ifPresent(room -> room.leave(session.getId()));
        }
    }

    private void sendControl(WebSocketSession session, DocumentWsControlMessage control) {
        try {
            session.sendMessage(codec.encodeControl(control));
        } catch (IOException exception) {
            leaveSession(session);
        }
    }

    private void sendError(WebSocketSession session, UUID requestId, String code, String message) {
        sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.ERROR,
                requestId == null ? UUID.randomUUID() : requestId, null, null, null, code, message));
    }

    private int protocolVersion() {
        return properties.getWebsocket().getProtocolVersion();
    }

    private static long requireDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new DocumentRoomAccessException("documentId must be positive");
        }
        return documentId;
    }

    private static String protocolErrorCode(DocumentWsProtocolException exception) {
        return exception.getMessage().contains("protocol version")
                ? "DOCUMENT_PROTOCOL_VERSION_UNSUPPORTED" : "DOCUMENT_PROTOCOL_ERROR";
    }
}
