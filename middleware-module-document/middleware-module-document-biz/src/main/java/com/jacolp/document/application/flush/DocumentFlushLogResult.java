package com.jacolp.document.application.flush;

/** Result of one idempotent Redis Stream to MySQL log flush attempt. */
public record DocumentFlushLogResult(long documentId, int processedCount, long processedBytes, long deletedCount) {

    public static DocumentFlushLogResult empty(long documentId) {
        return new DocumentFlushLogResult(documentId, 0, 0L, 0L);
    }
}
