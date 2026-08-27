package com.jacolp.document.websocket;

import com.jacolp.common.security.context.CurrentPrincipal;
import com.jacolp.document.application.close.DocumentRoomLifecycleService;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.redis.DocumentPendingUpdate;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import com.jacolp.document.metrics.DocumentMetrics;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 已认证的文档协作端点；Java 只路由不透明的 Yjs 字节，绝不解析正文。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentWebSocketHandler extends AbstractWebSocketHandler {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    private final DocumentWsCodec codec;
    private final DocumentMapper documentMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentRoomManager roomManager;
    private final DocumentBootstrapService bootstrapService;
    private final DocumentSchedulePublisher schedulePublisher;
    private final DocumentSessionPresenceRegistry presenceRegistry;
    private final DocumentRoomLifecycleService lifecycleService;
    private final DocumentProperties properties;
    private final DocumentMetrics metrics;
    private final ConcurrentHashMap<String, Long> joinedDocumentIds = new ConcurrentHashMap<>();

    /** 创建不记录指标的文档 WebSocket 处理器，保留生产处理流程。 */
    public DocumentWebSocketHandler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                    DocumentRedisRepository documentRedisRepository, DocumentRoomManager roomManager,
                                    DocumentBootstrapService bootstrapService, DocumentSchedulePublisher schedulePublisher,
                                    DocumentSessionPresenceRegistry presenceRegistry,
                                    DocumentRoomLifecycleService lifecycleService, DocumentProperties properties) {
        this(codec, documentMapper, documentRedisRepository, roomManager, bootstrapService, schedulePublisher,
                presenceRegistry, lifecycleService, properties, DocumentMetrics.noop());
    }

    /** 创建带运行指标的文档 WebSocket 处理器。 */
    @Autowired
    public DocumentWebSocketHandler(DocumentWsCodec codec, DocumentMapper documentMapper,
                                    DocumentRedisRepository documentRedisRepository, DocumentRoomManager roomManager,
                                    DocumentBootstrapService bootstrapService, DocumentSchedulePublisher schedulePublisher,
                                    DocumentSessionPresenceRegistry presenceRegistry,
                                    DocumentRoomLifecycleService lifecycleService, DocumentProperties properties,
                                    DocumentMetrics metrics) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.bootstrapService = Objects.requireNonNull(bootstrapService, "bootstrapService must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
        this.presenceRegistry = Objects.requireNonNull(presenceRegistry, "presenceRegistry must not be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /** 解析控制帧并处理 JOIN、LEAVE、PING 及协议错误响应。 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DocumentWsControlMessage control;
        try {
            control = codec.decodeControl(message);
        } catch (DocumentWsProtocolException exception) {
            // 控制帧尚未通过解析，无法可信地使用客户端 requestId，只能生成新的错误关联 ID。
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            // 只有控制协议明确支持的操作才能改变 Room 或触发服务端响应，未知类型不能静默执行。
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

    /** 只接受客户端 CLIENT_UPDATE 和 AWARENESS 二进制帧，其他类型直接拒绝。 */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DocumentWsBinaryFrame frame;
        try {
            frame = codec.decodeBinary(message);
        } catch (DocumentWsProtocolException exception) {
            metrics.recordUpdateRejected();
            sendError(session, UUID.randomUUID(), protocolErrorCode(exception), exception.getMessage());
            return;
        }

        try {
            // CLIENT_UPDATE 进入持久化链路，AWARENESS 只做实时广播；快照/历史帧只能由服务端发送。
            if (frame.type() == DocumentWsFrameType.CLIENT_UPDATE) {
                acceptClientUpdate(session, frame);
            } else if (frame.type() == DocumentWsFrameType.AWARENESS) {
                broadcastAwareness(session, frame);
            } else {
                metrics.recordUpdateRejected();
                sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", "binary frame type is not accepted from clients");
            }
        } catch (DocumentRoomAccessException exception) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_FORBIDDEN", exception.getMessage());
        } catch (RuntimeException exception) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_ACCEPT_FAILED", "document update could not be accepted");
        }
    }

    /** 连接关闭后释放本机会话、presence 和延迟关闭状态。 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leaveSession(session);
    }

    /** 传输异常与正常关闭使用同一套会话清理流程。 */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        leaveSession(session);
    }

    /** 校验个人范围后建立 Room 归属，按顺序发送 bootstrap 并在完成后激活会话。 */
    private void handleJoin(WebSocketSession session, DocumentWsControlMessage control) {
        long documentId = requireDocumentId(control.documentId());
        Long alreadyJoinedDocumentId = joinedDocumentIds.get(session.getId());
        // 一个 WebSocket 会话只能绑定一个文档；切换文档必须先断开旧会话，避免状态串写。
        if (alreadyJoinedDocumentId != null) {
            if (alreadyJoinedDocumentId != documentId) {
                throw new DocumentRoomAccessException("a WebSocket session may join only one document at a time");
            }
            // 同一会话重复 JOIN 时不重放文档，只确认已有的 Room 成员关系仍有效。
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null));
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
            return;
        }

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        DocumentDO document = documentMapper.selectActiveByIdAndTeamId(documentId, principal.userId());
        if (document == null) {
            // 查询同时带个人 scope 和 deleted 过滤；对不存在与越权文档统一返回不可访问，避免泄露资源存在性。
            sendError(session, control.requestId(), "DOCUMENT_NOT_FOUND", "document does not exist or is not accessible");
            return;
        }

        DocumentRoom room = roomManager.getOrCreate(documentId, principal.userId());
        // 先登记本地 Room 和 presence，再发送 bootstrap，确保期间 CLOSE 不会误判无人在线。
        room.join(session, principal);
        joinedDocumentIds.put(session.getId(), documentId);
        roomManager.refreshRuntimeMetrics();
        try {
            presenceRegistry.register(documentId, session.getId());
            lifecycleService.reopen(document, principal.userId());
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.JOIN_ACCEPTED,
                    control.requestId(), documentId, null, null, null, null));
            // 在快照、持久化日志和 Redis 待写入更新全部发送完前，会话不会进入 active；
            // 此期间收到的二进制编辑会被拒绝。
            bootstrapService.sendBootstrap(document, room.requireSession(session.getId()).session());
            room.markActive(session.getId());
            sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.SYNC_COMPLETE,
                    control.requestId(), documentId, null, null, null, null));
        } catch (RuntimeException exception) {
            // bootstrap 任一步骤失败都不能留下半初始化会话，否则后续更新会绕过完整恢复流程。
            leaveSession(session);
            throw exception;
        }
    }

    /** 校验客户端更新、先写 Redis，再确认/广播并安排异步刷盘。 */
    private void acceptClientUpdate(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        // 先限制单次更新大小，避免异常客户端占满 Redis Stream、出站队列或下游合并服务。
        if (frame.payload().length > properties.getWebsocket().getMaxUpdateBytes()) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_UPDATE_TOO_LARGE", "Yjs update exceeds configured maximum size");
            return;
        }
        // 空更新没有任何可合并内容，却会污染 ACK、广播和刷盘链路，因此直接拒绝。
        if (frame.payload().length == 0) {
            metrics.recordUpdateRejected();
            sendError(session, frame.eventId(), "DOCUMENT_PROTOCOL_ERROR", "Yjs update must not be empty");
            return;
        }

        CurrentPrincipal principal = DocumentWebSocketHandshakeInterceptor.requirePrincipal(session.getAttributes());
        long now = System.currentTimeMillis();
        String redisOpId = documentRedisRepository.appendPendingUpdate(new DocumentPendingUpdate(room.documentId(),
                frame.payload(), frame.eventId().toString(), principal.userId(), principal.clientId(), now));

        // Redis Stream 写入成功即代表服务端已接收；后续 MySQL 审计更新失败仍由恢复链路继续处理。
        // 先将不透明更新写入 Redis，随后才确认和广播；之后的 HTTP 或 WebSocket 发送失败时，
        // 已接收的编辑仍可被恢复。
        int modified = documentMapper.updateLastModificationIfActive(room.documentId(), principal.userId(),
                LocalDateTime.now(APPLICATION_ZONE), principal.userId());
        if (modified != 1) {
            // Redis 已经接收更新，但数据库 scope/active 条件未命中；不发送 ACK，避免客户端误以为更新已完成。
            throw new DocumentRoomAccessException("document no longer accepts updates in this personal scope");
        }
        saveRoomMeta(room.documentId(), principal.userId(), now);
        sendControl(room.requireSession(session.getId()).session(), new DocumentWsControlMessage(protocolVersion(),
                DocumentWsControlType.UPDATE_ACCEPTED, frame.eventId(), room.documentId(), frame.eventId(), redisOpId, null, null));
        metrics.recordUpdateAccepted();
        room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.CRDT_UPDATE,
                frame.eventId(), frame.payload())), session.getId());
        scheduleFlushLog(room.documentId());
    }

    /** 将 awareness 数据广播给同一 Room 的其他已同步会话，不进入持久化链路。 */
    private void broadcastAwareness(WebSocketSession session, DocumentWsBinaryFrame frame) {
        DocumentRoom room = requireActiveRoom(session);
        room.broadcast(codec.encodeBinary(new DocumentWsBinaryFrame(DocumentWsFrameType.AWARENESS,
                frame.eventId(), frame.payload())), session.getId());
    }

    /** 获取已 JOIN 且已完成 bootstrap 的本机会话 Room。 */
    private DocumentRoom requireActiveRoom(WebSocketSession session) {
        Long documentId = joinedDocumentIds.get(session.getId());
        if (documentId == null) {
            // 未记录 JOIN 关系的会话不能提交任何二进制数据，即使握手本身已经通过认证。
            throw new DocumentRoomAccessException("WebSocket session has not joined a document");
        }
        DocumentRoom room = roomManager.find(documentId)
                .orElseThrow(() -> new DocumentRoomAccessException("document Room is not available"));
        if (room.requireSession(session.getId()).syncStatus() != DocumentSessionSyncStatus.ACTIVE) {
            // bootstrap 尚未完整发送时，客户端状态可能落后于服务端；必须等 SYNC_COMPLETE 后才接受编辑。
            throw new DocumentRoomAccessException("document session is still synchronizing");
        }
        return room;
    }

    /** 根据数据库审计时间刷新 Redis Room Meta，并生成新的 reopen 令牌。 */
    private void updateRoomMeta(DocumentDO document, long userId) {
        long lastModifiedAt = document.getLastModifyTime() == null
                ? System.currentTimeMillis()
                : document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli();
        saveRoomMeta(document.getId(), userId, lastModifiedAt);
    }

    /** 以认证用户作为个人范围保存 Room Meta，并拒绝 Redis 中的范围冲突。 */
    private void saveRoomMeta(long documentId, long userId, long lastModifiedAt) {
        Optional<DocumentRoomMeta> existing = documentRedisRepository.findRoomMeta(documentId);
        if (existing.isPresent() && existing.get().teamId() != userId) {
            // Redis 中已有的 Room scope 与认证用户不一致时拒绝覆盖，防止错误或越权数据被重新绑定。
            throw new DocumentRoomAccessException("Redis room meta does not match authenticated personal scope");
        }
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(documentId, userId, false,
                UUID.randomUUID().toString(), lastModifiedAt, userId));
    }

    /** 幂等移除会话并在本机最后离开时请求延迟 CLOSE。 */
    private void leaveSession(WebSocketSession session) {
        Long documentId = joinedDocumentIds.remove(session.getId());
        presenceRegistry.unregister(session.getId());
        // 未完成 JOIN 的连接也会触发关闭回调；清理 presence 后无需再操作不存在的 Room。
        if (documentId != null) {
            roomManager.find(documentId).ifPresent(room -> {
                // 只有本次确实移除了最后一个本机会话，才需要发起延迟 CLOSE；重复关闭回调不能重复推进生命周期。
                if (room.leave(session.getId()) && room.sessionCount() == 0) {
                    try {
                        lifecycleService.requestClose(documentId, room.teamId());
                    } catch (RuntimeException exception) {
                        log.warn("Could not request delayed CLOSE for documentId={}: {}", documentId, exception.getMessage());
                    }
                }
                roomManager.refreshRuntimeMetrics();
            });
        }
    }

    /** 编码并发送控制帧；发送失败时释放对应会话。 */
    private void sendControl(WebSocketSession session, DocumentWsControlMessage control) {
        try {
            session.sendMessage(codec.encodeControl(control));
        } catch (IOException exception) {
            leaveSession(session);
        }
    }

    /** 发布 FLUSH_LOG 信号；Rabbit 失败不回滚已写入 Redis 的更新。 */
    private void scheduleFlushLog(long documentId) {
        try {
            schedulePublisher.scheduleFlushLog(documentId);
        } catch (RuntimeException exception) {
            // Redis Stream 追加已保存这条更新；恢复扫描会在之后为同一文档重新发布 FLUSH_LOG 信号。
            log.warn("Could not schedule FLUSH_LOG for documentId={}: {}", documentId, exception.getMessage());
        }
    }

    /** 发送统一格式的错误控制帧，并为缺失 requestId 生成关联 ID。 */
    private void sendError(WebSocketSession session, UUID requestId, String code, String message) {
        // 某些错误发生在解析 requestId 之前，因此统一补齐 ID 让客户端仍能关联错误响应。
        sendControl(session, new DocumentWsControlMessage(protocolVersion(), DocumentWsControlType.ERROR,
                requestId == null ? UUID.randomUUID() : requestId, null, null, null, code, message));
    }

    /** 返回当前配置的 WebSocket 协议版本。 */
    private int protocolVersion() {
        return properties.getWebsocket().getProtocolVersion();
    }

    /** 校验控制帧中的文档 ID 为正数。 */
    private static long requireDocumentId(Long documentId) {
        if (documentId == null || documentId <= 0) {
            // 文档 ID 既是 Room/Redis key 的组成部分，也是数据库查询范围，空值和非正数都不能继续传播。
            throw new DocumentRoomAccessException("documentId must be positive");
        }
        return documentId;
    }

    /** 把协议版本错误映射为客户端可识别的错误码。 */
    private static String protocolErrorCode(DocumentWsProtocolException exception) {
        // 版本错误需要让客户端明确知道应升级协议，其余解析失败统一归入协议错误。
        return exception.getMessage().contains("protocol version")
                ? "DOCUMENT_PROTOCOL_VERSION_UNSUPPORTED" : "DOCUMENT_PROTOCOL_ERROR";
    }
}
