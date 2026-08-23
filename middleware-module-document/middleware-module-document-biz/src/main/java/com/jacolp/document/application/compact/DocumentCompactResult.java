package com.jacolp.document.application.compact;

/** Result of a CAS-protected immutable snapshot compaction attempt. */
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
