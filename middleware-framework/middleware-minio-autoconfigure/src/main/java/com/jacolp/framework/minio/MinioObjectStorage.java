package com.jacolp.framework.minio;

/**
 * Common byte-oriented MinIO operations for modules that do not need direct SDK access.
 *
 * <p>The caller owns key naming, retention, and business metadata. This API deliberately does
 * not create buckets or infer a business bucket from an object key.</p>
 */
public interface MinioObjectStorage {

    /** Reads an object and rejects a response larger than {@code maxBytes}. */
    byte[] read(String bucket, String objectKey, long maxBytes);

    /** Stores an object at the exact key supplied by the caller, creating its bucket if needed. */
    void write(String bucket, String objectKey, byte[] content, String contentType);

    /** Deletes the object at the exact key supplied by the caller. */
    void delete(String bucket, String objectKey);
}
