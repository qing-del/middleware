package com.jacolp.document.application.flush;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import com.jacolp.document.infrastructure.redis.DocumentRedisRepository;
import com.jacolp.document.infrastructure.redis.StoredDocumentPendingUpdate;
import com.jacolp.document.metrics.DocumentMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Moves one bounded cutoff of accepted binary updates from Redis into the durable MySQL log. */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentFlushLogService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final DocumentRedisRepository documentRedisRepository;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final TransactionTemplate transactionTemplate;
    private final DocumentProperties properties;
    private final DocumentMetrics metrics;

    public DocumentFlushLogService(DocumentRedisRepository documentRedisRepository,
                                   DocumentOpLogMapper documentOpLogMapper,
                                   TransactionTemplate transactionTemplate,
                                   DocumentProperties properties) {
        this(documentRedisRepository, documentOpLogMapper, transactionTemplate, properties, DocumentMetrics.noop());
    }

    @Autowired
    public DocumentFlushLogService(DocumentRedisRepository documentRedisRepository,
                                   DocumentOpLogMapper documentOpLogMapper,
                                   TransactionTemplate transactionTemplate,
                                   DocumentProperties properties, DocumentMetrics metrics) {
        this.documentRedisRepository = Objects.requireNonNull(documentRedisRepository, "documentRedisRepository must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Persists a bounded Redis Stream prefix and deletes exactly that prefix only after the database transaction commits.
     * A duplicate replay is safe because the database has unique keys for both Redis and client operation IDs.
     */
    public DocumentFlushLogResult flush(long documentId) {
        requirePositive(documentId);
        Timer.Sample sample = metrics.startFlush();
        boolean failed = true;
        try {
            List<StoredDocumentPendingUpdate> pending = documentRedisRepository.readPendingUpdates(documentId,
                    Math.max(1, properties.getFlushLog().getBatchSize()));
            FlushBatch batch = toBoundedBatch(documentId, pending);
            if (batch.logs().isEmpty()) {
                failed = false;
                return DocumentFlushLogResult.empty(documentId);
            }

            transactionTemplate.execute(status -> {
                documentOpLogMapper.insertBatchIgnoringDuplicates(batch.logs());
                return null;
            });
            long deleted = documentRedisRepository.deletePendingUpdates(documentId, batch.redisOpIds());
            failed = false;
            return new DocumentFlushLogResult(documentId, batch.logs().size(), batch.binaryBytes(), deleted);
        } finally {
            metrics.completeFlush(sample, failed);
        }
    }

    private FlushBatch toBoundedBatch(long documentId, List<StoredDocumentPendingUpdate> pending) {
        int maxBytes = properties.getFlushLog().getMaxBatchBytes();
        if (maxBytes <= 0) {
            throw new IllegalStateException("jacolp.document.flush-log.max-batch-bytes must be positive");
        }
        List<DocumentOpLogDO> logs = new ArrayList<>();
        List<String> redisOpIds = new ArrayList<>();
        long bytes = 0L;
        for (StoredDocumentPendingUpdate pendingUpdate : pending) {
            int updateBytes = pendingUpdate.update().updateData().length;
            if (!logs.isEmpty() && bytes + updateBytes > maxBytes) {
                break;
            }
            logs.add(toLog(documentId, pendingUpdate));
            redisOpIds.add(pendingUpdate.redisOpId());
            bytes += updateBytes;
        }
        return new FlushBatch(List.copyOf(logs), List.copyOf(redisOpIds), bytes);
    }

    private static DocumentOpLogDO toLog(long documentId, StoredDocumentPendingUpdate pending) {
        return new DocumentOpLogDO(null, documentId, pending.redisOpId(), pending.update().clientUpdateId(),
                pending.update().updateData(), pending.update().operatorId(), pending.update().operatorType(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(pending.update().createdAt()), APPLICATION_ZONE));
    }

    private static void requirePositive(long documentId) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
    }

    private record FlushBatch(List<DocumentOpLogDO> logs, List<String> redisOpIds, long binaryBytes) {
    }
}
