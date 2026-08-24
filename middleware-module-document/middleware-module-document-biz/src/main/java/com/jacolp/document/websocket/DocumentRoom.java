package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.api.model.DocumentRoomLifecycleState;
import com.jacolp.document.config.DocumentProperties;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** 仅保存运行时会话的容器，刻意不在 JVM 内保存 Yjs 文档或正文内容。 */
public class DocumentRoom {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final CloseStatus SLOW_CLIENT = new CloseStatus(1013, "document outbound queue exceeded");

    private final long documentId;
    private final long teamId;
    private final DocumentProperties properties;
    private final ConcurrentHashMap<String, DocumentSessionContext> sessions = new ConcurrentHashMap<>();
    private volatile DocumentRoomLifecycleState lifecycleState = DocumentRoomLifecycleState.OPEN;

    DocumentRoom(long documentId, long teamId, DocumentProperties properties) {
        if (documentId <= 0 || teamId <= 0) {
            throw new IllegalArgumentException("documentId and teamId must be positive");
        }
        this.documentId = documentId;
        this.teamId = teamId;
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public synchronized DocumentSessionContext join(WebSocketSession session, CurrentPrincipal principal) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        if (principal.userId() != teamId) {
            throw new DocumentRoomAccessException("document does not belong to the authenticated personal scope");
        }
        if (lifecycleState == DocumentRoomLifecycleState.CLOSED) {
            throw new DocumentRoomAccessException("document room is closed");
        }
        DocumentSessionContext existing = sessions.get(session.getId());
        if (existing != null) {
            return existing;
        }
        if (sessions.size() >= properties.getWebsocket().getMaxRoomSessions()) {
            throw new DocumentRoomLimitExceededException("document room session limit exceeded");
        }

        WebSocketSession boundedSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                properties.getWebsocket().getMaxSendQueueBytes(),
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        DocumentSessionContext context = new DocumentSessionContext(boundedSession, principal.userId());
        sessions.put(context.sessionId(), context);
        lifecycleState = DocumentRoomLifecycleState.ACTIVE;
        return context;
    }

    public synchronized boolean leave(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        DocumentSessionContext removed = sessions.remove(sessionId);
        if (removed != null && sessions.isEmpty()) {
            lifecycleState = DocumentRoomLifecycleState.PRE_CLOSE;
        }
        return removed != null;
    }

    public void markActive(String sessionId) {
        DocumentSessionContext context = requireSession(sessionId);
        context.markActive();
    }

    /** 仅当本机没有会话时开始最终关闭；之后的 JOIN 会重新打开这个 Room。 */
    public synchronized boolean beginClosingIfEmpty() {
        if (!sessions.isEmpty() || lifecycleState == DocumentRoomLifecycleState.CLOSED) {
            return false;
        }
        lifecycleState = DocumentRoomLifecycleState.CLOSING;
        return true;
    }

    public void broadcast(WebSocketMessage<?> message, String excludedSessionId) {
        Objects.requireNonNull(message, "message must not be null");
        for (DocumentSessionContext context : sessions.values()) {
            if (context.sessionId().equals(excludedSessionId)) {
                continue;
            }
            sendOrDisconnect(context, message);
        }
    }

    public DocumentSessionContext requireSession(String sessionId) {
        DocumentSessionContext context = sessions.get(sessionId);
        if (context == null) {
            throw new DocumentRoomAccessException("WebSocket session has not joined this document");
        }
        return context;
    }

    public long documentId() {
        return documentId;
    }

    public long teamId() {
        return teamId;
    }

    public DocumentRoomLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public int sessionCount() {
        return sessions.size();
    }

    public Collection<DocumentSessionContext> sessions() {
        return List.copyOf(sessions.values());
    }

    private void sendOrDisconnect(DocumentSessionContext context, WebSocketMessage<?> message) {
        WebSocketSession session = context.session();
        if (!session.isOpen()) {
            leave(context.sessionId());
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException exception) {
            leave(context.sessionId());
            try {
                if (session.isOpen()) {
                    session.close(SLOW_CLIENT);
                }
            } catch (IOException ignored) {
                // 传输失败已经通过从 Room 移除该会话处理，无需再次向上抛出。
            }
        }
    }
}
