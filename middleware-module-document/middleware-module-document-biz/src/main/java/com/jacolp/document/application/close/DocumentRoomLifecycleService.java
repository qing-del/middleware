package com.jacolp.document.application.close;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import com.jacolp.document.messaging.DocumentSchedulePublisher;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Maintains durable close-token state independently from the JVM-local Room container. */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRoomLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRoomLifecycleService.class);
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentSchedulePublisher schedulePublisher;

    public DocumentRoomLifecycleService(DocumentRedisRepository documentRedisRepository,
                                        DocumentSchedulePublisher schedulePublisher) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.schedulePublisher = Objects.requireNonNull(schedulePublisher, "schedulePublisher must not be null");
    }

    /** A JOIN invalidates every old delayed CLOSE token before bootstrap completes. */
    public void reopen(DocumentDO document, long userId) {
        long lastModifiedAt = document.getLastModifyTime() == null ? System.currentTimeMillis()
                : document.getLastModifyTime().atZone(APPLICATION_ZONE).toInstant().toEpochMilli();
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(document.getId(), userId, false,
                UUID.randomUUID().toString(), lastModifiedAt, userId));
    }

    /** A local last leave is enough to request close; consumer presence checks decide whether it may execute. */
    public void requestClose(long documentId, long teamId) {
        DocumentRoomMeta previous = documentRedisRepository.findRoomMeta(documentId)
                .orElse(new DocumentRoomMeta(documentId, teamId, false, null, System.currentTimeMillis(), teamId));
        if (previous.teamId() != teamId) {
            throw new IllegalStateException("document room meta personal scope does not match close requester");
        }
        String closeToken = UUID.randomUUID().toString();
        documentRedisRepository.saveRoomMeta(new DocumentRoomMeta(documentId, teamId, true, closeToken,
                previous.lastModifyTime(), previous.lastModifyUserId()));
        try {
            schedulePublisher.scheduleClose(documentId, closeToken);
        } catch (RuntimeException exception) {
            log.warn("Could not schedule CLOSE for documentId={}: {}", documentId, exception.getMessage());
        }
    }

    public boolean isCurrentClose(long documentId, String closeToken) {
        return closeToken != null && documentRedisRepository.findRoomMeta(documentId)
                .map(meta -> meta.closeRequested() && closeToken.equals(meta.closeToken()))
                .orElse(false);
    }

    @Scheduled(fixedDelayString = "${jacolp.document.flush-log.recovery-scan-ms:30000}")
    public void rescheduleOutstandingCloses() {
        for (DocumentRoomMeta meta : documentRedisRepository.findRoomMetas()) {
            if (!meta.closeRequested() || meta.closeToken() == null) {
                continue;
            }
            try {
                schedulePublisher.scheduleClose(meta.documentId(), meta.closeToken());
            } catch (RuntimeException exception) {
                log.warn("Could not recover CLOSE schedule for documentId={}: {}", meta.documentId(), exception.getMessage());
            }
        }
    }
}
