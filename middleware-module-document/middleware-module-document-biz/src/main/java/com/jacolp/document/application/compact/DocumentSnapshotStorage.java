package com.jacolp.document.application.compact;

import com.jacolp.document.config.DocumentProperties;
import com.jacolp.framework.minio.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Binary-only MinIO adapter for immutable Yjs snapshots. */
@Component
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentSnapshotStorage {

    private static final Logger log = LoggerFactory.getLogger(DocumentSnapshotStorage.class);

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final DocumentProperties documentProperties;

    public DocumentSnapshotStorage(MinioClient minioClient, MinioProperties minioProperties,
                                   DocumentProperties documentProperties) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient must not be null");
        this.minioProperties = Objects.requireNonNull(minioProperties, "minioProperties must not be null");
        this.documentProperties = Objects.requireNonNull(documentProperties, "documentProperties must not be null");
    }

    public byte[] read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket()).object(objectKey).build())) {
            return readBounded(stream);
        } catch (DocumentSnapshotStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentSnapshotStorageException("could not read document snapshot", exception);
        }
    }

    /** Writes a new immutable object and returns its never-reused object key. */
    public String write(long documentId, byte[] yjsState) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        Objects.requireNonNull(yjsState, "yjsState must not be null");
        validateSnapshotSize(yjsState.length);
        String objectKey = "document/%d/state/%s.bin".formatted(documentId, UUID.randomUUID());
        try (ByteArrayInputStream stream = new ByteArrayInputStream(yjsState)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket()).object(objectKey)
                    .stream(stream, yjsState.length, -1)
                    .contentType("application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception exception) {
            throw new DocumentSnapshotStorageException("could not write document snapshot", exception);
        }
    }

    private byte[] readBounded(InputStream stream) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (output.size() + read > documentProperties.getSnapshot().getMaxBytes()) {
                    throw new DocumentSnapshotStorageException("document snapshot exceeds configured maximum size");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String bucket() {
        String bucket = minioProperties.getBucket().get("document");
        if (bucket == null || bucket.isBlank()) {
            throw new DocumentSnapshotStorageException("jacolp.minio.bucket.document is required for document snapshots");
        }
        return bucket;
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
