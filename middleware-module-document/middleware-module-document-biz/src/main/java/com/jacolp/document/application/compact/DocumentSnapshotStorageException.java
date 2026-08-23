package com.jacolp.document.application.compact;

/** Raised when an immutable document snapshot cannot be read or written in MinIO. */
public class DocumentSnapshotStorageException extends RuntimeException {

    public DocumentSnapshotStorageException(String message) {
        super(message);
    }

    public DocumentSnapshotStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
