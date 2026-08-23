package com.jacolp.document.application.flush;

import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-emits flush signals after publisher failures or broker losses; Redis remains the source of truth. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentFlushRecoveryScanner {

    private static final Logger log = LoggerFactory.getLogger(DocumentFlushRecoveryScanner.class);

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentSchedulePublisher schedulePublisher;

    public DocumentFlushRecoveryScanner(DocumentRedisRepository documentRedisRepository,
                                        DocumentSchedulePublisher schedulePublisher) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
    }

    @Scheduled(fixedDelayString = "${jacolp.document.flush-log.recovery-scan-ms:30000}")
    public void scanAndReschedule() {
        for (var meta : documentRedisRepository.findRoomMetas()) {
            try {
                if (documentRedisRepository.pendingUpdateCount(meta.documentId()) > 0) {
                    schedulePublisher.scheduleFlushLog(meta.documentId());
                }
            } catch (RuntimeException exception) {
                log.warn("Could not recover FLUSH_LOG schedule for documentId={}: {}", meta.documentId(),
                        exception.getMessage());
            }
        }
    }
}
