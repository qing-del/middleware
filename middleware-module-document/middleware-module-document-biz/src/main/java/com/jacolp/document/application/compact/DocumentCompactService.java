package com.jacolp.document.application.compact;

import com.jacolp.document.application.yjs.YjsMergeClient;
import com.jacolp.document.config.DocumentProperties;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentOpLogDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentOpLogMapper;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Compacts one bounded durable log cutoff into a new immutable Yjs snapshot. */
@Service
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentCompactService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCompactService.class);

    private final DocumentMapper documentMapper;
    private final DocumentOpLogMapper documentOpLogMapper;
    private final DocumentSnapshotStorage snapshotStorage;
    private final YjsMergeClient yjsMergeClient;
    private final DocumentProperties properties;

    public DocumentCompactService(DocumentMapper documentMapper, DocumentOpLogMapper documentOpLogMapper,
                                  DocumentSnapshotStorage snapshotStorage, YjsMergeClient yjsMergeClient,
                                  DocumentProperties properties) {
        this.documentMapper = Objects.requireNonNull(documentMapper, "documentMapper must not be null");
        this.documentOpLogMapper = Objects.requireNonNull(documentOpLogMapper, "documentOpLogMapper must not be null");
        this.snapshotStorage = Objects.requireNonNull(snapshotStorage, "snapshotStorage must not be null");
        this.yjsMergeClient = Objects.requireNonNull(yjsMergeClient, "yjsMergeClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public DocumentCompactResult compact(long documentId) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        DocumentDO document = documentMapper.selectById(documentId);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            return DocumentCompactResult.noUpdates(documentId);
        }
        long basePersistedLogId = document.getPersistedLogId() == null ? 0L : document.getPersistedLogId();
        List<DocumentOpLogDO> updates = documentOpLogMapper.selectByDocumentIdAfterId(documentId, basePersistedLogId,
                Math.max(1, properties.getFlushLog().getBatchSize()));
        if (updates == null || updates.isEmpty()) {
            return DocumentCompactResult.noUpdates(documentId);
        }
        long cutoffLogId = requireOrderedCutoff(basePersistedLogId, updates);
        byte[] mergedState = yjsMergeClient.merge(snapshotStorage.read(document.getContentObjectKey()),
                updates.stream().map(DocumentOpLogDO::getUpdateData).toList());
        String objectKey = snapshotStorage.write(documentId, mergedState);
        int casAffected = documentMapper.updateSnapshotPointerIfPersistedLogId(documentId, basePersistedLogId,
                objectKey, cutoffLogId);
        if (casAffected != 1) {
            return new DocumentCompactResult(documentId, DocumentCompactResult.Status.CAS_LOST, cutoffLogId, objectKey);
        }
        try {
            documentOpLogMapper.deleteByDocumentIdThroughId(documentId, cutoffLogId);
        } catch (RuntimeException exception) {
            // The new pointer makes these rows logically obsolete; retry/cleanup may delete them later.
            log.warn("Snapshot pointer advanced but op log cleanup failed for documentId={}, cutoffLogId={}: {}",
                    documentId, cutoffLogId, exception.getMessage());
        }
        return new DocumentCompactResult(documentId, DocumentCompactResult.Status.COMPACTED, cutoffLogId, objectKey);
    }

    private static long requireOrderedCutoff(long basePersistedLogId, List<DocumentOpLogDO> updates) {
        long previousId = basePersistedLogId;
        for (DocumentOpLogDO update : updates) {
            if (update.getId() == null || update.getId() <= previousId || update.getUpdateData() == null
                    || update.getUpdateData().length == 0) {
                throw new IllegalStateException("document op log batch is not a valid ordered Yjs cutoff");
            }
            previousId = update.getId();
        }
        return previousId;
    }
}
