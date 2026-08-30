package com.jacolp.document.websocket;

import com.jacolp.document.application.access.DocumentAccess;
import com.jacolp.document.enums.DocumentPermission;
import java.util.Objects;
import org.springframework.web.socket.WebSocketSession;

/** 单个会话的运行时元数据；JVM 内不保存任何 Yjs 更新历史。 */
public final class DocumentSessionContext {

    private final WebSocketSession session;
    private final long userId;
    private volatile SessionAccess access;
    private volatile DocumentSessionSyncStatus syncStatus = DocumentSessionSyncStatus.SYNCING;

    /** 创建尚未完成 bootstrap 的同步中会话上下文。 */
    DocumentSessionContext(WebSocketSession session, long userId, DocumentAccess documentAccess) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        this.access = toSessionAccess(Objects.requireNonNull(documentAccess, "documentAccess must not be null"));
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

    /** 返回当前会话在最近一次 ACL 校验中获得的文档权限。 */
    public DocumentPermission permission() {
        return access.permission();
    }

    /** 返回当前会话是否为文档所有者。 */
    public boolean owner() {
        return access.owner();
    }

    /** 返回当前会话是否允许提交 CRDT 更新。 */
    public boolean canWrite() {
        SessionAccess current = access;
        return current.owner() || current.permission().canWrite();
    }

    /** 用重复 JOIN 或写入前的最新 ACL 结果刷新会话能力。 */
    void updateAccess(DocumentAccess documentAccess) {
        access = toSessionAccess(Objects.requireNonNull(documentAccess, "documentAccess must not be null"));
    }

    /** 返回 bootstrap 同步状态。 */
    public DocumentSessionSyncStatus syncStatus() {
        return syncStatus;
    }

    /** 将会话从 SYNCING 切换为 ACTIVE。 */
    void markActive() {
        syncStatus = DocumentSessionSyncStatus.ACTIVE;
    }

    private static SessionAccess toSessionAccess(DocumentAccess documentAccess) {
        return new SessionAccess(documentAccess.permission(), documentAccess.owner());
    }

    private record SessionAccess(DocumentPermission permission, boolean owner) {
    }
}
