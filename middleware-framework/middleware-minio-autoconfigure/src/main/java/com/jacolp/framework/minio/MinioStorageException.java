package com.jacolp.framework.minio;

/** Runtime exception used by the module-neutral MinIO storage API. */
public class MinioStorageException extends RuntimeException {

    public MinioStorageException(String message) {
        super(message);
    }

    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
