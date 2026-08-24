package com.jacolp.framework.minio;

import java.util.Objects;

final class DefaultMinioBucketResolver implements MinioBucketResolver {

    private final MinioProperties properties;

    DefaultMinioBucketResolver(MinioProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String requireBucket(String logicalBucketName) {
        if (logicalBucketName == null || logicalBucketName.isBlank()) {
            throw new MinioStorageException("logical MinIO bucket name must not be blank");
        }
        String bucket = properties.getBucket().get(logicalBucketName);
        if (bucket == null || bucket.isBlank()) {
            throw new MinioStorageException("jacolp.minio.bucket.%s must be configured".formatted(logicalBucketName));
        }
        return bucket;
    }
}
