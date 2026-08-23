package com.jacolp.document.application.close;

/** Result of one delayed CLOSE attempt. Non-closed results are safe no-ops and may be retried. */
public record DocumentCloseResult(long documentId, Status status) {

    public enum Status {
        IGNORED,
        REOPENED,
        CLOSED
    }
}
