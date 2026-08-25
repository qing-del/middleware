package com.jacolp.document.application.flush;

/** 一次幂等地将 Redis Stream 更新写入 MySQL 操作日志的结果。 */
public record DocumentFlushLogResult(long documentId, int processedCount, long processedBytes, long deletedCount) {

    /** 创建表示当前 Redis Stream 没有待刷盘更新的结果。 */
    public static DocumentFlushLogResult empty(long documentId) {
        return new DocumentFlushLogResult(documentId, 0, 0L, 0L);
    }
}
