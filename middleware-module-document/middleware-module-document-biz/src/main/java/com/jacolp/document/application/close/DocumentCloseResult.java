package com.jacolp.document.application.close;

/** 一次延迟 CLOSE 的执行结果；未关闭结果都是可安全重试的空操作。 */
public record DocumentCloseResult(long documentId, Status status) {

    /** 延迟关闭执行状态；{@code IGNORED} 和 {@code REOPENED} 都允许后续重试。 */
    public enum Status {
        IGNORED,
        REOPENED,
        CLOSED
    }
}
