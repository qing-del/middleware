package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Owns JVM-local Room runtime state; durable content remains in Redis/MySQL/MinIO. */
@Component
public class DocumentRoomManager {

    private final DocumentProperties properties;
    private final ConcurrentHashMap<Long, DocumentRoom> rooms = new ConcurrentHashMap<>();

    public DocumentRoomManager(DocumentProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public DocumentRoom getOrCreate(long documentId, long teamId) {
        return rooms.compute(documentId, (ignored, existing) -> {
            if (existing == null) {
                return new DocumentRoom(documentId, teamId, properties);
            }
            if (existing.teamId() != teamId) {
                throw new DocumentRoomAccessException("document Room personal scope does not match");
            }
            return existing;
        });
    }

    public Optional<DocumentRoom> find(long documentId) {
        return Optional.ofNullable(rooms.get(documentId));
    }

    public boolean removeIfEmpty(long documentId) {
        AtomicBoolean removed = new AtomicBoolean();
        rooms.computeIfPresent(documentId, (ignored, room) -> {
            if (room.sessionCount() == 0) {
                removed.set(true);
                return null;
            }
            return room;
        });
        return removed.get();
    }
}
