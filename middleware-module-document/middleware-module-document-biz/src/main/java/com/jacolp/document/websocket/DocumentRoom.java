package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.access.DocumentAccess;
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
    private final long ownerUserId;
    private final DocumentProperties properties;
    private final ConcurrentHashMap<String, DocumentSessionContext> sessions = new ConcurrentHashMap<>();
    private volatile DocumentRoomLifecycleState lifecycleState = DocumentRoomLifecycleState.OPEN;

    /** 创建只保存会话运行态的本机 Room。 */
    DocumentRoom(long documentId, long ownerUserId, DocumentProperties properties) {
        if (documentId <= 0 || ownerUserId <= 0) {
            // Room ID 会直接参与本地索引和 Redis key 生成，非法范围不能创建运行时容器。
            throw new IllegalArgumentException("documentId and ownerUserId must be positive");
        }
        this.documentId = documentId;
        this.ownerUserId = ownerUserId;
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 校验 Room 状态和已计算的文档访问结果后加入会话；重复加入同一 session 保持幂等。 */
    public synchronized DocumentSessionContext join(WebSocketSession session, CurrentPrincipal principal,
                                                    DocumentAccess documentAccess) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(documentAccess, "documentAccess must not be null");
        if (documentAccess.document().getId() == null || documentAccess.document().getId() != documentId
                || !Objects.equals(documentAccess.document().getOwnerUserId(), ownerUserId)) {
            // Room 只接受同一篇文档和同一所有者快照的访问结果，避免调用方把 ACL 结果串到其他 Room。
            throw new DocumentRoomAccessException("document access does not match this Room");
        }
        if (lifecycleState == DocumentRoomLifecycleState.CLOSED) {
            // CLOSED 表示运行态已经清理；调用方应先通过新的 JOIN 流程重新建立 Room 状态。
            throw new DocumentRoomAccessException("document room is closed");
        }
        DocumentSessionContext existing = sessions.get(session.getId());
        if (existing != null) {
            // 同一连接重复 JOIN 复用原上下文，同时刷新权限以反映最新 ACL。
            existing.updateAccess(documentAccess);
            return existing;
        }
        if (sessions.size() >= properties.getWebsocket().getMaxRoomSessions()) {
            // 在创建有界 WebSocket 包装器前拒绝超限连接，防止 Room 内存和广播压力继续增长。
            throw new DocumentRoomLimitExceededException("document room session limit exceeded");
        }

        // 用带上限的装饰器隔离慢客户端，避免单个出站队列拖住其他协作者。
        WebSocketSession boundedSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                properties.getWebsocket().getMaxSendQueueBytes(),
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE);
        DocumentSessionContext context = new DocumentSessionContext(boundedSession, principal.userId(), documentAccess);
        sessions.put(context.sessionId(), context);
        lifecycleState = DocumentRoomLifecycleState.ACTIVE;
        return context;
    }

    /** 移除本机会话；最后一个离开者把 Room 推进到 PRE_CLOSE。 */
    public synchronized boolean leave(String sessionId) {
        if (sessionId == null) {
            // 关闭回调可能在握手未完成时触发，空 session ID 不对应任何可清理的成员。
            return false;
        }
        DocumentSessionContext removed = sessions.remove(sessionId);
        if (removed != null && sessions.isEmpty()) {
            // 只有确实移除了最后一个成员才进入 PRE_CLOSE，供异步关闭流程继续做全局校验。
            lifecycleState = DocumentRoomLifecycleState.PRE_CLOSE;
        }
        return removed != null;
    }

    /** 将已完成 bootstrap 的会话标记为可接收客户端更新。 */
    public void markActive(String sessionId) {
        DocumentSessionContext context = requireSession(sessionId);
        context.markActive();
    }

    /** 仅当本机没有会话时开始最终关闭；之后的 JOIN 会重新打开这个 Room。 */
    public synchronized boolean beginClosingIfEmpty() {
        if (!sessions.isEmpty() || lifecycleState == DocumentRoomLifecycleState.CLOSED) {
            // 有本机会话时不能关闭；CLOSED 也不能重复进入关闭流程，避免清理已结束的 Room。
            return false;
        }
        lifecycleState = DocumentRoomLifecycleState.CLOSING;
        return true;
    }

    /** 向 Room 中除发送者外的会话广播消息，慢或已关闭会话会被清理。 */
    public void broadcast(WebSocketMessage<?> message, String excludedSessionId) {
        Objects.requireNonNull(message, "message must not be null");
        for (DocumentSessionContext context : sessions.values()) {
            if (context.sessionId().equals(excludedSessionId)) {
                // 广播调用者已经拥有这条消息，跳过自身可避免重复处理和回环。
                continue;
            }
            sendOrDisconnect(context, message);
        }
    }

    /** 获取指定会话，否则返回统一的 Room 访问异常。 */
    public DocumentSessionContext requireSession(String sessionId) {
        DocumentSessionContext context = sessions.get(sessionId);
        if (context == null) {
            // 只允许 Room 成员访问会话上下文，防止未 JOIN 或已离开的连接发送更新。
            throw new DocumentRoomAccessException("WebSocket session has not joined this document");
        }
        return context;
    }

    /** 返回文档主键。 */
    public long documentId() {
        return documentId;
    }

    /** 返回文档所有者 ID，仅用于 Room 生命周期和运行态审计。 */
    public long ownerUserId() {
        return ownerUserId;
    }

    /** 返回本机 Room 生命周期状态。 */
    public DocumentRoomLifecycleState lifecycleState() {
        return lifecycleState;
    }

    /** 返回当前本机会话数。 */
    public int sessionCount() {
        return sessions.size();
    }

    /** 返回会话快照，避免调用方直接修改内部并发容器。 */
    public Collection<DocumentSessionContext> sessions() {
        return List.copyOf(sessions.values());
    }

    /** 尝试向会话发送消息；传输失败时移除并关闭该慢会话。 */
    private void sendOrDisconnect(DocumentSessionContext context, WebSocketMessage<?> message) {
        WebSocketSession session = context.session();
        if (!session.isOpen()) {
            // 发送前再次检查连接状态，及时移除已由容器关闭的失效成员。
            leave(context.sessionId());
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException exception) {
            // 单个客户端发送失败不应阻塞 Room 中其他协作者；先移除，再尝试关闭底层连接。
            leave(context.sessionId());
            try {
                if (session.isOpen()) {
                    // 发送异常后连接可能已经被底层关闭，只有仍开放时才发出慢客户端关闭码。
                    session.close(SLOW_CLIENT);
                }
            } catch (IOException ignored) {
                // 传输失败已经通过从 Room 移除该会话处理，无需再次向上抛出。
            }
        }
    }
}
