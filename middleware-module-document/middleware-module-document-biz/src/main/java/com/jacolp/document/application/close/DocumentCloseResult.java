package com.jacolp.document.application.close;

/** 一次延迟 CLOSE 的执行结果；未关闭结果都是可安全重试的空操作。 */
public record DocumentCloseResult(long documentId, Status status) {

    public enum Status {
        IGNORED,
        REOPENED,
        CLOSED
    }
}
