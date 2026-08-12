package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.port.out.CoreAgentAuthorizationCodeStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Enabled implementation that revokes the exact Redis-backed CORE AGENT authorization-code pointer. */
@Service
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "true")
public final class CoreAgentAccountAuthorizationStateRevocationService
        implements AccountAuthorizationStateRevocationService {

    private static final String CORE_AGENT_CLIENT_ID = CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID;

    private final CoreAgentAuthorizationCodeStore authorizationCodeStore;

    public CoreAgentAccountAuthorizationStateRevocationService(CoreAgentAuthorizationCodeStore authorizationCodeStore) {
        this.authorizationCodeStore = Objects.requireNonNull(authorizationCodeStore, "authorizationCodeStore");
    }

    @Override
    public void revokeCurrentCoreAgentAuthorizationCode(Long userId) {
        authorizationCodeStore.invalidateCurrent(requireUserId(userId), CORE_AGENT_CLIENT_ID);
    }

    @Override
    public void revokeForSecurityFieldChange(Long userId) {
        revokeCurrentCoreAgentAuthorizationCode(userId);
    }

    private static Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return userId;
    }
}
