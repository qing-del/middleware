package com.jacolp.document.application.close;

import com.jacolp.document.application.compact.DocumentCompactResult;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.metrics.DocumentMetrics;
import com.jacolp.document.websocket.DocumentRoomManager;
import com.jacolp.document.websocket.DocumentSessionPresenceRegistry;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 执行最终 FLUSH_LOG 与 COMPACT，并在两次关闭校验都通过后清理运行时状态。 */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentCloseService {

    private final DocumentRoomLifecycleService lifecycleService;
    private final DocumentSessionPresenceRegistry presenceRegistry;
    private final DocumentRoomManager roomManager;
    private final DocumentFlushLogService flushLogService;
    private final DocumentCompactService compactService;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentMetrics metrics;

    public DocumentCloseService(DocumentRoomLifecycleService lifecycleService,
                                DocumentSessionPresenceRegistry presenceRegistry,
                                DocumentRoomManager roomManager,
                                DocumentFlushLogService flushLogService,
                                DocumentCompactService compactService,
                                DocumentRedisRepository documentRedisRepository) {
        this(lifecycleService, presenceRegistry, roomManager, flushLogService, compactService,
                documentRedisRepository, DocumentMetrics.noop());
    }

    @Autowired
    public DocumentCloseService(DocumentRoomLifecycleService lifecycleService,
                                DocumentSessionPresenceRegistry presenceRegistry,
                                DocumentRoomManager roomManager,
                                DocumentFlushLogService flushLogService,
                                DocumentCompactService compactService,
                                DocumentRedisRepository documentRedisRepository, DocumentMetrics metrics) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.presenceRegistry = Objects.requireNonNull(presenceRegistry, "presenceRegistry must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.compactService = Objects.requireNonNull(compactService, "compactService must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public DocumentCloseResult close(long documentId, String closeToken) {
        try {
            if (!closeGuardsPass(documentId, closeToken) || !roomManager.beginClosingIfEmpty(documentId)) {
                return new DocumentCloseResult(documentId, DocumentCloseResult.Status.IGNORED);
            }
            flushAll(documentId);
            compactAll(documentId);
            // 持久化可能耗时较长，因此再次检查：若期间有会话重连，就撤销本次关闭，
            // 保留 Room 与 Redis 运行时状态供新会话继续使用。
            if (!closeGuardsPass(documentId, closeToken) || !roomManager.hasNoLocalSessions(documentId)) {
                return new DocumentCloseResult(documentId, DocumentCloseResult.Status.REOPENED);
            }
            roomManager.removeIfEmpty(documentId);
            documentRedisRepository.deleteRoomRuntime(documentId);
            return new DocumentCloseResult(documentId, DocumentCloseResult.Status.CLOSED);
        } catch (RuntimeException exception) {
            metrics.recordCloseFailed();
            throw exception;
        }
    }

    private boolean closeGuardsPass(long documentId, String closeToken) {
        return lifecycleService.isCurrentClose(documentId, closeToken) && presenceRegistry.count(documentId) == 0
                && roomManager.hasNoLocalSessions(documentId);
    }

    private void flushAll(long documentId) {
        DocumentFlushLogResult result;
        do {
            result = flushLogService.flush(documentId);
        } while (result.processedCount() > 0);
    }

    private void compactAll(long documentId) {
        while (true) {
            DocumentCompactResult result = compactService.compact(documentId);
            if (result.status() == DocumentCompactResult.Status.NO_UPDATES) {
                return;
            }
        }
    }
}
