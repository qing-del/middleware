package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Redis TTL leases make WebSocket presence visible to CLOSE consumers on other core nodes. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSessionPresenceRegistry {

    private final String instanceToken = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, String> localPresenceKeys = new ConcurrentHashMap<>();
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentProperties properties;

    public DocumentSessionPresenceRegistry(DocumentRedisRepository documentRedisRepository,
                                           DocumentProperties properties) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public void register(long documentId, String sessionId) {
        if (documentId <= 0 || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("documentId and sessionId are required for presence");
        }
        String newKey = "document:presence:%d:%s:%s".formatted(documentId, instanceToken, sessionId);
        String previous = localPresenceKeys.put(sessionId, newKey);
        if (previous != null && !previous.equals(newKey)) {
            documentRedisRepository.deletePresence(previous);
        }
        documentRedisRepository.savePresence(newKey, leaseTtlMs());
    }

    public void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String presenceKey = localPresenceKeys.remove(sessionId);
        if (presenceKey != null) {
            documentRedisRepository.deletePresence(presenceKey);
        }
    }

    public long count(long documentId) {
        return documentRedisRepository.countPresence(documentId);
    }

    @Scheduled(fixedDelayString = "${jacolp.document.session-presence-refresh-ms:10000}")
    public void refreshLocalLeases() {
        long ttl = leaseTtlMs();
        localPresenceKeys.values().forEach(key -> documentRedisRepository.savePresence(key, ttl));
    }

    private long leaseTtlMs() {
        long refresh = properties.getSessionPresenceRefreshMs();
        if (refresh <= 0) {
            throw new IllegalStateException("jacolp.document.session-presence-refresh-ms must be positive");
        }
        return Math.max(properties.getCloseDelayMs() * 2L, refresh * 3L);
    }
}
