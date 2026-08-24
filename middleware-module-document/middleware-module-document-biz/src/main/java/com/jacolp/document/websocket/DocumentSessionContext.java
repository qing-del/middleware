package com.jacolp.document.websocket;

import java.util.Objects;
import org.springframework.web.socket.WebSocketSession;

/** 单个会话的运行时元数据；JVM 内不保存任何 Yjs 更新历史。 */
public final class DocumentSessionContext {

    private final WebSocketSession session;
    private final long userId;
    private volatile DocumentSessionSyncStatus syncStatus = DocumentSessionSyncStatus.SYNCING;

    DocumentSessionContext(WebSocketSession session, long userId) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
    }

    public String sessionId() {
        return session.getId();
    }

    public WebSocketSession session() {
        return session;
    }

    public long userId() {
        return userId;
    }

    public DocumentSessionSyncStatus syncStatus() {
        return syncStatus;
    }

    void markActive() {
        syncStatus = DocumentSessionSyncStatus.ACTIVE;
    }
}
