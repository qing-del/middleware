package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.constant.UserConstant;
import com.jacolp.middleware.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.middleware.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationCodeIssueRequest;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationCodeState;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.module.system.biz.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.module.system.biz.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import com.jacolp.module.system.biz.application.port.out.CoreAgentAuthorizationCodeStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Issues and atomically stores one validated CORE AGENT authorization code. */
@Service
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "true")
public final class CoreAgentAuthorizationCodeIssueService {

    private final Clock clock;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final CoreAgentRegisteredClientPolicyResolver policyResolver;
    private final AuthorizationAccountRepository accountRepository;
    private final AccountGrantTypeResolver accountGrantTypeResolver;
    private final EffectiveRolePermissionResolver rolePermissionResolver;
    private final CoreAgentConsentScopeService consentScopeService;
    private final CoreAgentAuthorizationCodeStore authorizationCodeStore;

    public CoreAgentAuthorizationCodeIssueService(
            Clock clock,
            SecureOAuth2TokenGenerator tokenGenerator,
            CoreAgentRegisteredClientPolicyResolver policyResolver,
            AuthorizationAccountRepository accountRepository,
            AccountGrantTypeResolver accountGrantTypeResolver,
            EffectiveRolePermissionResolver rolePermissionResolver,
            CoreAgentConsentScopeService consentScopeService,
            CoreAgentAuthorizationCodeStore authorizationCodeStore) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.accountGrantTypeResolver = Objects.requireNonNull(accountGrantTypeResolver, "accountGrantTypeResolver");
        this.rolePermissionResolver = Objects.requireNonNull(rolePermissionResolver, "rolePermissionResolver");
        this.consentScopeService = Objects.requireNonNull(consentScopeService, "consentScopeService");
        this.authorizationCodeStore = Objects.requireNonNull(authorizationCodeStore, "authorizationCodeStore");
    }

    public IssuedCoreAgentAuthorizationCode issue(CoreAgentAuthorizationCodeIssueRequest request) {
        Objects.requireNonNull(request, "request");
        CoreAgentRegisteredClientPolicy policy = requiredPolicy();
        if (!policy.clientId().equals(request.clientId()) || !policy.redirectUri().equals(request.redirectUri())) {
            throw rejected();
        }
        enforceSocketAllowed(policy, request.socketRemoteAddress());

        AuthorizationAccount account = currentAccount(request.authenticatedUserId());
        verifyAuthorizationCodeGrant(account);
        EffectiveRolePermissions effectiveRole = rolePermissionResolver.resolve(account.roleId());
        if (effectiveRole == null || !account.roleId().equals(effectiveRole.roleId())) {
            throw new IllegalStateException("CORE AGENT effective role identity is inconsistent");
        }
        List<String> scopes = consentScopeService.confirm(effectiveRole, policy, request.requestedScopes(), null,
                request.submittedOptionalScopes());
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalStateException("CORE AGENT consent scope resolution returned no scopes");
        }

        String rawCode = tokenGenerator.newOpaqueToken();
        if (rawCode == null) {
            throw new IllegalStateException("CORE AGENT authorization-code generator returned null");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(policy.authorizationCodeTimeToLive());
        CoreAgentAuthorizationCodeState state = new CoreAgentAuthorizationCodeState(rawCode, policy.clientId(),
                policy.redirectUri(), scopes, request.codeChallenge(), request.codeChallengeMethod(),
                request.socketRemoteAddress(), request.oauthState(), issuedAt, expiresAt, snapshot(account));
        IssuedCoreAgentAuthorizationCode issued = authorizationCodeStore.replaceCurrent(state);
        if (issued == null || !rawCode.equals(issued.rawCode()) || !expiresAt.equals(issued.expiresAt())) {
            throw new IllegalStateException("CORE AGENT authorization-code store returned an inconsistent issuance");
        }
        return issued;
    }

    private CoreAgentRegisteredClientPolicy requiredPolicy() {
        CoreAgentRegisteredClientPolicy policy = policyResolver.resolve(
                CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID);
        if (policy == null || !CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID.equals(policy.clientId())) {
            throw new IllegalStateException("CORE AGENT registered client policy is invalid");
        }
        return policy;
    }

    private static void enforceSocketAllowed(CoreAgentRegisteredClientPolicy policy, String socketRemoteAddress) {
        final ClientAllowedIpPolicy allowedIps;
        try {
            allowedIps = ClientAllowedIpPolicy.parse(policy.allowedIps());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("CORE AGENT allowed IP policy is invalid", exception);
        }
        try {
            if (!allowedIps.allows(socketRemoteAddress)) {
                throw rejected();
            }
        } catch (IllegalArgumentException exception) {
            throw rejected();
        }
    }

    private AuthorizationAccount currentAccount(Long expectedUserId) {
        Optional<AuthorizationAccount> accountOptional = accountRepository.findById(expectedUserId);
        if (accountOptional == null) {
            throw new IllegalStateException("CORE AGENT authorization account lookup returned null");
        }
        if (accountOptional.isEmpty()) {
            throw rejected();
        }
        AuthorizationAccount account = accountOptional.get();
        if (!expectedUserId.equals(account.userId())) {
            throw new IllegalStateException("CORE AGENT authorization account identity is inconsistent");
        }
        return account;
    }

    private void verifyAuthorizationCodeGrant(AuthorizationAccount account) {
        if (account.status() != UserConstant.ACTIVE_STATUS) {
            throw rejected();
        }
        final boolean allowed;
        try {
            allowed = accountGrantTypeResolver.allows(AccountGrantTypeResolver.AUTHORIZATION_CODE,
                    account.extraGrantTypes());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CORE AGENT authorization account grant configuration is invalid");
        }
        if (!allowed) {
            throw rejected();
        }
    }

    private static CoreAgentAuthorizationAccountSnapshot snapshot(AuthorizationAccount account) {
        return new CoreAgentAuthorizationAccountSnapshot(account.userId(), account.username(), account.roleId(),
                account.passwordHash(), account.email(), account.extraGrantTypes(), account.status());
    }

    private static CoreAgentAuthorizationCodeIssueRejectedException rejected() {
        return new CoreAgentAuthorizationCodeIssueRejectedException();
    }
}
