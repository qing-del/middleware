package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import com.jacolp.document.websocket.protocol.DocumentWsBinaryFrame;
import com.jacolp.document.websocket.protocol.DocumentWsCodec;
import com.jacolp.document.websocket.protocol.DocumentWsFrameType;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectStorage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/** 按快照、持久化日志和 Redis 待写入更新的顺序发送可恢复 bootstrap，不解析任何 Yjs 内容。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentBootstrapService {

    private static final UUID BOOTSTRAP_EVENT_ID = new UUID(0L, 0L);

    private final MinioObjectStorage minioObjectStorage;
    private final MinioBucketResolver minioBucketResolver;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentWsCodec codec;
    private final DocumentProperties properties;

    /** 创建按“快照 → MySQL 日志 → Redis Stream”顺序发送 bootstrap 的服务。 */
    public DocumentBootstrapService(MinioObjectStorage minioObjectStorage, MinioBucketResolver minioBucketResolver,
                                    DocumentOpLogMapper documentOpLogMapper,
                                    DocumentRedisRepository documentRedisRepository,
                                    DocumentWsCodec codec, DocumentProperties properties) {
        this.minioObjectStorage = Objects.requireNonNull(minioObjectStorage, "minioObjectStorage must not be null");
        this.minioBucketResolver = Objects.requireNonNull(minioBucketResolver, "minioBucketResolver must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** 向新会话发送可恢复的全部内容，并把底层读取/传输错误统一包装。 */
    public void sendBootstrap(DocumentDO document, WebSocketSession session) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(session, "session must not be null");
        long documentId = requireDocumentId(document);
        try {
            sendSnapshotIfPresent(document, session);
            sendDurableUpdates(documentId, document.getPersistedLogId(), session);
            sendPendingUpdates(documentId, session);
        } catch (IOException exception) {
            throw new DocumentBootstrapException("could not send document bootstrap", exception);
        } catch (DocumentBootstrapException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentBootstrapException("could not read document bootstrap", exception);
        }
    }

    /** 如果存在快照对象，则先发送快照状态作为客户端基线。 */
    private void sendSnapshotIfPresent(DocumentDO document, WebSocketSession session) throws Exception {
        String objectKey = document.getContentObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        String bucket = minioBucketResolver.requireBucket("document");
        byte[] snapshot = minioObjectStorage.read(bucket, objectKey, properties.getSnapshot().getMaxBytes());
        send(session, DocumentWsFrameType.SNAPSHOT_STATE, BOOTSTRAP_EVENT_ID, snapshot);
    }

    /** 分批发送快照位点之后的 MySQL 持久化更新。 */
    private void sendDurableUpdates(long documentId, Long persistedLogId, WebSocketSession session) throws IOException {
        long afterId = persistedLogId == null ? 0L : persistedLogId;
        int batchSize = Math.max(1, properties.getFlushLog().getBatchSize());
        while (true) {
            List<DocumentOpLogDO> updates = documentOpLogMapper.selectByDocumentIdAfterId(documentId, afterId, batchSize);
            if (updates == null || updates.isEmpty()) {
                return;
            }
            for (DocumentOpLogDO update : updates) {
                if (update.getId() == null || update.getId() <= afterId || update.getUpdateData() == null) {
                    throw new DocumentBootstrapException("document op log contains an invalid bootstrap update");
                }
                send(session, DocumentWsFrameType.BOOTSTRAP_UPDATE, BOOTSTRAP_EVENT_ID, update.getUpdateData());
                afterId = update.getId();
            }
            if (updates.size() < batchSize) {
                return;
            }
        }
    }

    /** 发送所有仍在 Redis Stream 中的更新，覆盖尚未完成 FLUSH_LOG 的编辑。 */
    private void sendPendingUpdates(long documentId, WebSocketSession session) throws IOException {
        // 这里刻意读取所有可见 Stream 条目：重连时不能因为 FLUSH_LOG 尚未消费下一批，
        // 就遗漏一条已经接收成功的更新。
        List<StoredDocumentPendingUpdate> updates = documentRedisRepository.readPendingUpdates(documentId, Integer.MAX_VALUE);
        for (StoredDocumentPendingUpdate update : updates) {
            send(session, DocumentWsFrameType.BOOTSTRAP_UPDATE, BOOTSTRAP_EVENT_ID, update.update().updateData());
        }
    }

    /** 使用统一 codec 发送一个带明确帧类型和事件 ID 的二进制帧。 */
    private void send(WebSocketSession session, DocumentWsFrameType type, UUID eventId, byte[] yjsBytes) throws IOException {
        session.sendMessage(codec.encodeBinary(new DocumentWsBinaryFrame(type, eventId, yjsBytes)));
    }

    /** 校验 bootstrap 来源文档具有可用主键。 */
    private static long requireDocumentId(DocumentDO document) {
        if (document.getId() == null || document.getId() <= 0) {
            throw new IllegalArgumentException("document must have a positive ID");
        }
        return document.getId();
    }
}
