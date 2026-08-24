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

/** Sends a recoverable document bootstrap without interpreting any Yjs payload. */
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

    private void sendSnapshotIfPresent(DocumentDO document, WebSocketSession session) throws Exception {
        String objectKey = document.getContentObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        String bucket = minioBucketResolver.requireBucket("document");
        byte[] snapshot = minioObjectStorage.read(bucket, objectKey, properties.getSnapshot().getMaxBytes());
        send(session, DocumentWsFrameType.SNAPSHOT_STATE, BOOTSTRAP_EVENT_ID, snapshot);
    }

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

    private void sendPendingUpdates(long documentId, WebSocketSession session) throws IOException {
        // This is intentionally an all-visible-stream read: reconnect must not omit an accepted
        // update merely because FLUSH_LOG has not consumed the next batch yet.
        List<StoredDocumentPendingUpdate> updates = documentRedisRepository.readPendingUpdates(documentId, Integer.MAX_VALUE);
        for (StoredDocumentPendingUpdate update : updates) {
            send(session, DocumentWsFrameType.BOOTSTRAP_UPDATE, BOOTSTRAP_EVENT_ID, update.update().updateData());
        }
    }

    private void send(WebSocketSession session, DocumentWsFrameType type, UUID eventId, byte[] yjsBytes) throws IOException {
        session.sendMessage(codec.encodeBinary(new DocumentWsBinaryFrame(type, eventId, yjsBytes)));
    }

    private static long requireDocumentId(DocumentDO document) {
        if (document.getId() == null || document.getId() <= 0) {
            throw new IllegalArgumentException("document must have a positive ID");
        }
        return document.getId();
    }
}
