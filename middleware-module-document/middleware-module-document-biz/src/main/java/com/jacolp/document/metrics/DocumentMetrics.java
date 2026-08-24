package com.jacolp.document.metrics;

import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.DocumentRoomMeta;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 协作持久化链路的低基数遥测指标，避免文档 ID 等高基数标签进入监控系统。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentMetrics {

    private final MeterRegistry meterRegistry;
    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final AtomicInteger websocketSessions = new AtomicInteger();
    private final AtomicInteger activeRooms = new AtomicInteger();
    private final Counter updateAccepted;
    private final Counter updateRejected;
    private final Counter flushFailed;
    private final Counter compactFailed;
    private final Counter closeFailed;
    private final Timer flushDuration;
    private final Timer compactDuration;
    private final Timer yjsMergeDuration;
    private final DistributionSummary snapshotBytes;

    public DocumentMetrics(MeterRegistry meterRegistry, DocumentRedisRepository documentRedisRepository,
                           DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository,
                "documentRedisRepository must not be null");
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        updateAccepted = Counter.builder("document_update_accept_total").register(meterRegistry);
        updateRejected = Counter.builder("document_update_reject_total").register(meterRegistry);
        flushFailed = Counter.builder("document_flush_log_failed_total").register(meterRegistry);
        compactFailed = Counter.builder("document_compact_failed_total").register(meterRegistry);
        closeFailed = Counter.builder("document_close_failed_total").register(meterRegistry);
        flushDuration = Timer.builder("document_flush_log_duration").register(meterRegistry);
        compactDuration = Timer.builder("document_compact_duration").register(meterRegistry);
        yjsMergeDuration = Timer.builder("yjs_merge_service_duration").register(meterRegistry);
        snapshotBytes = DistributionSummary.builder("document_snapshot_bytes").register(meterRegistry);
        Gauge.builder("document_ws_sessions", websocketSessions, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("document_active_rooms", activeRooms, AtomicInteger::get).register(meterRegistry);
        Gauge.builder("document_pending_update_count", this, ignored -> pendingUpdateCount()).register(meterRegistry);
        Gauge.builder("document_unmerged_op_count", this, ignored -> unmergedOpCount()).register(meterRegistry);
    }

    private DocumentMetrics() {
        meterRegistry = null;
        documentRedisRepository = null;
        documentMapper = null;
        documentOpLogMapper = null;
        updateAccepted = null;
        updateRejected = null;
        flushFailed = null;
        compactFailed = null;
        closeFailed = null;
        flushDuration = null;
        compactDuration = null;
        yjsMergeDuration = null;
        snapshotBytes = null;
    }

    public static DocumentMetrics noop() {
        return NoopHolder.INSTANCE;
    }

    public void recordUpdateAccepted() {
        if (meterRegistry != null) updateAccepted.increment();
    }

    public void recordUpdateRejected() {
        if (meterRegistry != null) updateRejected.increment();
    }

    public Timer.Sample startFlush() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    public void completeFlush(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(flushDuration);
        if (failed) flushFailed.increment();
    }

    public Timer.Sample startCompact() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    public void completeCompact(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(compactDuration);
        if (failed) compactFailed.increment();
    }

    public Timer.Sample startYjsMerge() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }

    public void completeYjsMerge(Timer.Sample sample, boolean failed) {
        if (sample == null) return;
        sample.stop(yjsMergeDuration);
    }

    public void recordCloseFailed() {
        if (meterRegistry != null) closeFailed.increment();
    }

    public void recordSnapshotBytes(long bytes) {
        if (meterRegistry != null && bytes >= 0) snapshotBytes.record(bytes);
    }

    public void updateRuntimeCounts(int currentActiveRooms, int currentWebsocketSessions) {
        if (meterRegistry == null) return;
        activeRooms.set(Math.max(0, currentActiveRooms));
        websocketSessions.set(Math.max(0, currentWebsocketSessions));
    }

    private double pendingUpdateCount() {
        if (meterRegistry == null) return Double.NaN;
        try {
            return activeDocumentIds().stream().mapToLong(documentRedisRepository::pendingUpdateCount).sum();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private double unmergedOpCount() {
        if (meterRegistry == null) return Double.NaN;
        try {
            long total = 0L;
            for (Long documentId : activeDocumentIds()) {
                DocumentDO document = documentMapper.selectById(documentId);
                if (document != null && !Boolean.TRUE.equals(document.getDeleted())) {
                    total += documentOpLogMapper.countByDocumentIdAfterId(documentId,
                            document.getPersistedLogId() == null ? 0L : document.getPersistedLogId());
                }
            }
            return total;
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private Set<Long> activeDocumentIds() {
        Set<Long> documentIds = new HashSet<>();
        for (DocumentRoomMeta meta : documentRedisRepository.findRoomMetas()) {
            documentIds.add(meta.documentId());
        }
        return documentIds;
    }

    private static final class NoopHolder {
        private static final DocumentMetrics INSTANCE = new DocumentMetrics();
    }
}
