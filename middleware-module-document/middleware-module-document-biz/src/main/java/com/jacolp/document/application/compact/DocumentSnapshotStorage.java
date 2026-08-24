package com.jacolp.document.application.compact;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.framework.minio.MinioBucketResolver;
import com.jacolp.framework.minio.MinioObjectStorage;
import com.jacolp.framework.minio.MinioStorageException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 只读写二进制内容的 MinIO 适配器，用于保存不可变 Yjs 快照。 */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSnapshotStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentSnapshotStorage.class);

    private final MinioObjectStorage minioObjectStorage;
    private final MinioBucketResolver minioBucketResolver;
    private final DocumentProperties documentProperties;

    public DocumentSnapshotStorage(MinioObjectStorage minioObjectStorage, MinioBucketResolver minioBucketResolver,
                                   DocumentProperties documentProperties) {
        this.minioObjectStorage = Objects.requireNonNull(minioObjectStorage, "minioObjectStorage must not be null");
        this.minioBucketResolver = Objects.requireNonNull(minioBucketResolver, "minioBucketResolver must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
    }

    public byte[] read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return minioObjectStorage.read(bucket(), objectKey, documentProperties.getSnapshot().getMaxBytes());
        } catch (DocumentSnapshotStorageException exception) {
            throw exception;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not read document snapshot", exception);
        }
    }

    /** 写入一个新快照对象，并返回永不复用的对象键。 */
    public String write(long documentId, byte[] yjsState) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(yjsState, "yjsState must not be null");
        validateSnapshotSize(yjsState.length);
        String objectKey = "document/%d/state/%s.bin".formatted(documentId, UUID.randomUUID());
        try {
            minioObjectStorage.write(bucket(), objectKey, yjsState, "application/octet-stream");
            return objectKey;
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("could not write document snapshot", exception);
        }
    }

    private String bucket() {
        try {
            return minioBucketResolver.requireBucket("document");
        } catch (MinioStorageException exception) {
            throw new DocumentSnapshotStorageException("jacolp.minio.bucket.document is required for document snapshots", exception);
        }
    }

    private void validateSnapshotSize(int bytes) {
        if (bytes > documentProperties.getSnapshot().getMaxBytes()) {
            throw new DocumentSnapshotStorageException("document snapshot exceeds configured maximum size");
        }
        if (bytes > documentProperties.getSnapshot().getWarnBytes()) {
            log.warn("Document snapshot size {} exceeds warn threshold {}", bytes,
                    documentProperties.getSnapshot().getWarnBytes());
        }
    }
}
