package com.jacolp.module.system.biz.application.port.out;

/** Internal outcome of one atomic email-code issuance attempt. */
public enum EmailLoginCodeIssueRateLimitDecision {
    ALLOWED,
    COOLDOWN,
    WINDOW_LIMIT
}
