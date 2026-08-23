package com.jacolp.document.application.close;

import com.jacolp.document.application.compact.DocumentCompactResult;
import com.jacolp.document.application.compact.DocumentCompactService;
import com.jacolp.document.application.flush.DocumentFlushLogResult;
import com.jacolp.document.application.flush.DocumentFlushLogService;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.websocket.DocumentRoomManager;
import com.jacolp.document.websocket.DocumentSessionPresenceRegistry;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Executes final FLUSH_LOG + COMPACT and removes runtime state only after every close guard passes twice. */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentCloseService {

    private final DocumentRoomLifecycleService lifecycleService;
    private final DocumentSessionPresenceRegistry presenceRegistry;
    private final DocumentRoomManager roomManager;
    private final DocumentFlushLogService flushLogService;
    private final DocumentCompactService compactService;
    private final DocumentRedisRepository documentRedisRepository;

    public DocumentCloseService(DocumentRoomLifecycleService lifecycleService,
                                DocumentSessionPresenceRegistry presenceRegistry,
                                DocumentRoomManager roomManager,
                                DocumentFlushLogService flushLogService,
                                DocumentCompactService compactService,
                                DocumentRedisRepository documentRedisRepository) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.presenceRegistry = Objects.requireNonNull(presenceRegistry, "presenceRegistry must not be null");
        this.roomManager = Objects.requireNonNull(roomManager, "roomManager must not be null");
        this.flushLogService = Objects.requireNonNull(flushLogService, "flushLogService must not be null");
        this.compactService = Objects.requireNonNull(compactService, "compactService must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
    }

    public DocumentCloseResult close(long documentId, String closeToken) {
        if (!closeGuardsPass(documentId, closeToken) || !roomManager.beginClosingIfEmpty(documentId)) {
            return new DocumentCloseResult(documentId, DocumentCloseResult.Status.IGNORED);
        }
        flushAll(documentId);
        compactAll(documentId);
        if (!closeGuardsPass(documentId, closeToken) || !roomManager.hasNoLocalSessions(documentId)) {
            return new DocumentCloseResult(documentId, DocumentCloseResult.Status.REOPENED);
        }
        roomManager.removeIfEmpty(documentId);
        documentRedisRepository.deleteRoomRuntime(documentId);
        return new DocumentCloseResult(documentId, DocumentCloseResult.Status.CLOSED);
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
