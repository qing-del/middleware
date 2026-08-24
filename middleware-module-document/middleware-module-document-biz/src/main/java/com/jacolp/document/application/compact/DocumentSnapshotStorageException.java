package com.jacolp.document.application.compact;

/** 文档不可变快照无法从 MinIO 读取或写入时抛出。 */
public class DocumentSnapshotStorageException extends RuntimeException {

    public DocumentSnapshotStorageException(String message) {
        super(message);
    }

    public DocumentSnapshotStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
