package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeIssueRateLimitRequest;

/**
 * Atomically checks and counts both email and IP dimensions. Only {@link EmailLoginCodeIssueRateLimitDecision#ALLOWED}
 * consumes cooldown and issue-window capacity.
 */
public interface EmailLoginCodeIssueRateLimiter {
    EmailLoginCodeIssueRateLimitDecision tryAcquire(EmailLoginCodeIssueRateLimitRequest request);
}
