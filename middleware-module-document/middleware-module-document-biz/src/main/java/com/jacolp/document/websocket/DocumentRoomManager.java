package com.jacolp.document.websocket;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.metrics.DocumentMetrics;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 管理 JVM 本地 Room 运行时状态；可恢复内容仍存放在 Redis、MySQL 与 MinIO。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentRoomManager {

    private final DocumentProperties properties;
    private final DocumentMetrics metrics;
    private final ConcurrentHashMap<Long, DocumentRoom> rooms = new ConcurrentHashMap<>();

    public DocumentRoomManager(DocumentProperties properties) {
        this(properties, DocumentMetrics.noop());
    }

    @Autowired
    public DocumentRoomManager(DocumentProperties properties, DocumentMetrics metrics) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
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
        refreshRuntimeMetrics();
        return removed.get();
    }

    /** 本机没有 Room 只会发生在重启或最终清理后，两种情况都表示本 JVM 没有存活会话。 */
    public boolean hasNoLocalSessions(long documentId) {
        return find(documentId).map(room -> room.sessionCount() == 0).orElse(true);
    }

    public boolean beginClosingIfEmpty(long documentId) {
        return find(documentId).map(DocumentRoom::beginClosingIfEmpty).orElse(true);
    }

    /** 会话归属变化后由处理器调用；指标值仅代表当前 Java 实例。 */
    public void refreshRuntimeMetrics() {
        int sessions = 0;
        int active = 0;
        for (DocumentRoom room : rooms.values()) {
            int roomSessions = room.sessionCount();
            sessions += roomSessions;
            if (roomSessions > 0) active++;
        }
        metrics.updateRuntimeCounts(active, sessions);
    }
}
