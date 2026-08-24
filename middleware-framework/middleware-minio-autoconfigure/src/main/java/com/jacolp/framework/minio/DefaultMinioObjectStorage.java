package com.jacolp.framework.minio;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

final class DefaultMinioObjectStorage implements MinioObjectStorage {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;

    DefaultMinioObjectStorage(MinioClient minioClient) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient must not be null");
    }

    @Override
    public byte[] read(String bucket, String objectKey, long maxBytes) {
        validateLocation(bucket, objectKey);
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return readBounded(stream, maxBytes);
        } catch (MinioStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MinioStorageException("could not read MinIO object", exception);
        }
    }

    @Override
    public void write(String bucket, String objectKey, byte[] content, String contentType) {
        validateLocation(bucket, objectKey);
        Objects.requireNonNull(content, "content must not be null");
        String resolvedContentType = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE : contentType;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(stream, content.length, -1)
                    .contentType(resolvedContentType)
                    .build());
        } catch (Exception exception) {
            throw new MinioStorageException("could not write MinIO object", exception);
        }
    }

    /**
     * Provision the logical bucket lazily so a newly deployed environment does not need a
     * separate bucket-creation step before its first upload.
     *
     * <p>The create operation is intentionally idempotent across application instances: another
     * instance may create the bucket after {@code bucketExists} returns false.</p>
     */
    private void ensureBucketExists(String bucket) throws Exception {
        if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            return;
        }
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (ErrorResponseException exception) {
            String errorCode = exception.errorResponse().code();
            if (!"BucketAlreadyExists".equals(errorCode) && !"BucketAlreadyOwnedByYou".equals(errorCode)) {
                throw exception;
            }
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        validateLocation(bucket, objectKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new MinioStorageException("could not delete MinIO object", exception);
        }
    }

    private static byte[] readBounded(InputStream stream, long maxBytes) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                // 写入下一块之前先检查，超过配置的内存上限时调用方不会得到不完整对象。
                if (output.size() > maxBytes - read) {
                    throw new MinioStorageException("MinIO object exceeds configured maximum size");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void validateLocation(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
    }
}
