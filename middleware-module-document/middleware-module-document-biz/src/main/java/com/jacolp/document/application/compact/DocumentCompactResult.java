package com.jacolp.document.application.compact;

/** 一次由 CAS 保护的不可变快照压缩尝试结果。 */
public record DocumentCompactResult(long documentId, Status status, Long cutoffLogId, String objectKey) {

    public enum Status {
        NO_UPDATES,
        COMPACTED,
        CAS_LOST
    }

    public static DocumentCompactResult noUpdates(long documentId) {
        return new DocumentCompactResult(documentId, Status.NO_UPDATES, null, null);
    }
}
