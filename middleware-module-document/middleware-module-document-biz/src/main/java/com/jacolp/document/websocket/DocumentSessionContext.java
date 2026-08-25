package com.jacolp.document.websocket;

import java.util.Objects;
import org.springframework.web.socket.WebSocketSession;

/** 单个会话的运行时元数据；JVM 内不保存任何 Yjs 更新历史。 */
public final class DocumentSessionContext {

    private final WebSocketSession session;
    private final long userId;
    private volatile DocumentSessionSyncStatus syncStatus = DocumentSessionSyncStatus.SYNCING;

    /** 创建尚未完成 bootstrap 的同步中会话上下文。 */
    DocumentSessionContext(WebSocketSession session, long userId) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
    }

    /** 返回底层 WebSocket 会话 ID。 */
    public String sessionId() {
        return session.getId();
    }

    /** 返回受 Room 保护的 WebSocket 会话。 */
    public WebSocketSession session() {
        return session;
    }

    /** 返回握手认证得到的用户 ID。 */
    public long userId() {
        return userId;
    }

    /** 返回 bootstrap 同步状态。 */
    public DocumentSessionSyncStatus syncStatus() {
        return syncStatus;
    }

    /** 将会话从 SYNCING 切换为 ACTIVE。 */
    void markActive() {
        syncStatus = DocumentSessionSyncStatus.ACTIVE;
    }
}
