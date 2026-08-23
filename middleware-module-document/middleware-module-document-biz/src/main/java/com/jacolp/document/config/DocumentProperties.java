package com.jacolp.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime limits for the document collaboration pipeline. */
@ConfigurationProperties(prefix = "jacolp.document")
public class DocumentProperties {
    private boolean enabled;
    private long closeDelayMs = 30_000L;
    private Websocket websocket = new Websocket();
    private FlushLog flushLog = new FlushLog();
    private Compact compact = new Compact();
    private Snapshot snapshot = new Snapshot();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCloseDelayMs() { return closeDelayMs; }
    public void setCloseDelayMs(long closeDelayMs) { this.closeDelayMs = closeDelayMs; }
    public Websocket getWebsocket() { return websocket; }
    public void setWebsocket(Websocket websocket) { this.websocket = websocket == null ? new Websocket() : websocket; }
    public FlushLog getFlushLog() { return flushLog; }
    public void setFlushLog(FlushLog flushLog) { this.flushLog = flushLog == null ? new FlushLog() : flushLog; }
    public Compact getCompact() { return compact; }
    public void setCompact(Compact compact) { this.compact = compact == null ? new Compact() : compact; }
    public Snapshot getSnapshot() { return snapshot; }
    public void setSnapshot(Snapshot snapshot) { this.snapshot = snapshot == null ? new Snapshot() : snapshot; }

    public static class Websocket {
        private int protocolVersion = 1;
        private int maxUpdateBytes = 256 * 1024;
        private int maxRoomSessions = 50;
        private int maxSendQueueBytes = 4 * 1024 * 1024;

        public int getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
        public int getMaxUpdateBytes() { return maxUpdateBytes; }
        public void setMaxUpdateBytes(int maxUpdateBytes) { this.maxUpdateBytes = maxUpdateBytes; }
        public int getMaxRoomSessions() { return maxRoomSessions; }
        public void setMaxRoomSessions(int maxRoomSessions) { this.maxRoomSessions = maxRoomSessions; }
        public int getMaxSendQueueBytes() { return maxSendQueueBytes; }
        public void setMaxSendQueueBytes(int maxSendQueueBytes) { this.maxSendQueueBytes = maxSendQueueBytes; }
    }

    public static class FlushLog {
        private long delayMs = 2_000L;
        private int batchSize = 500;
        private int maxBatchBytes = 2 * 1024 * 1024;
        private long recoveryScanMs = 30_000L;

        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxBatchBytes() { return maxBatchBytes; }
        public void setMaxBatchBytes(int maxBatchBytes) { this.maxBatchBytes = maxBatchBytes; }
        public long getRecoveryScanMs() { return recoveryScanMs; }
        public void setRecoveryScanMs(long recoveryScanMs) { this.recoveryScanMs = recoveryScanMs; }
    }

    public static class Compact {
        private long intervalMs = 20_000L;
        private int maxUnmergedOps = 200;
        private int maxUnmergedBytes = 1024 * 1024;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getMaxUnmergedOps() { return maxUnmergedOps; }
        public void setMaxUnmergedOps(int maxUnmergedOps) { this.maxUnmergedOps = maxUnmergedOps; }
        public int getMaxUnmergedBytes() { return maxUnmergedBytes; }
        public void setMaxUnmergedBytes(int maxUnmergedBytes) { this.maxUnmergedBytes = maxUnmergedBytes; }
    }

    public static class Snapshot {
        private int warnBytes = 2 * 1024 * 1024;
        private int maxBytes = 10 * 1024 * 1024;

        public int getWarnBytes() { return warnBytes; }
        public void setWarnBytes(int warnBytes) { this.warnBytes = warnBytes; }
        public int getMaxBytes() { return maxBytes; }
        public void setMaxBytes(int maxBytes) { this.maxBytes = maxBytes; }
    }
}
