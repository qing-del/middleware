package com.jacolp.module.system.biz.application.authorization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Explicit disabled-mode implementation with no Redis dependency.
 *
 * <p>RS256-disabled deployments cannot issue new CORE AGENT authorization codes. A residual code retained
 * through a configuration switch is still rejected by its account snapshot during exchange after any security
 * field changes; this no-op must never be used when the RS256 authorization flow is enabled.</p>
 */
@Service
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "false", matchIfMissing = true)
public final class NoOpAccountAuthorizationStateRevocationService
        implements AccountAuthorizationStateRevocationService {

    @Override
    public void revokeCurrentCoreAgentAuthorizationCode(Long userId) {
        requireUserId(userId);
    }

    @Override
    public void revokeForSecurityFieldChange(Long userId) {
        revokeCurrentCoreAgentAuthorizationCode(userId);
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }
}
