package com.jacolp.module.system.biz.web.authorization;

import com.jacolp.module.system.biz.application.authorization.CoreAgentAuthorizationCodeIssueService;
import com.jacolp.module.system.biz.application.authorization.CoreAgentAuthorizationConsentService;
import com.jacolp.module.system.biz.application.authorization.CoreAgentBrowserAuthenticationToken;
import com.jacolp.module.system.biz.application.authorization.CoreAgentRegisteredClientPolicyResolver;
import com.jacolp.module.system.biz.application.authorization.EffectiveRolePermissionResolver;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationCodeIssueRequest;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationConsentDecision;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentBrowserAuthorizationTransaction;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentConsentScopeOptions;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentRegisteredClientPolicy;
import com.jacolp.module.system.biz.application.authorization.model.EffectiveRolePermissions;
import com.jacolp.module.system.biz.application.authorization.model.IssuedCoreAgentAuthorizationCode;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreAgentAuthorizationCodeRequestAuthenticationProviderTest {

    private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");
    private static final String REDIRECT_URI = "http://127.0.0.1:9090/oauth/callback";
    private static final String STATE = "client-state";
    private static final String VALUE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ISSUE_CODE = VALUE;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void completeConsentIssuesRedisCodeAndReturnsTheStandardSuccessToken() {
        Fixture fixture = fixture();
        when(fixture.consentService.prepare(eq(7L), any(), any(), eq(null))).thenReturn(reusedDecision());
        when(fixture.issueService.issue(any())).thenReturn(new IssuedCoreAgentAuthorizationCode(ISSUE_CODE,
                NOW.plus(Duration.ofMinutes(10))));

        Authentication result = fixture.provider.authenticate(request(fixture.session, false, null, null));

        assertThat(result).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationToken.class);
        OAuth2AuthorizationCodeRequestAuthenticationToken success =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(success.isAuthenticated()).isTrue();
        assertThat(success.getAuthorizationCode().getTokenValue()).isEqualTo(ISSUE_CODE);
        assertThat(success.getAuthorizationCode().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(success.getClientId()).isEqualTo("core_agent");
        assertThat(success.getRedirectUri()).isEqualTo(REDIRECT_URI);
        assertThat(success.getState()).isEqualTo(STATE);
        assertThat(success.getScopes()).containsExactly("note:read", "note:write");
        assertThat(fixture.session.getAttribute(HttpSessionCoreAgentAuthorizationTransactionStore.ATTRIBUTE_NAME)).isNull();

        org.mockito.ArgumentCaptor<CoreAgentAuthorizationCodeIssueRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(CoreAgentAuthorizationCodeIssueRequest.class);
        verify(fixture.issueService).issue(requestCaptor.capture());
        assertThat(requestCaptor.getValue().authenticatedUserId()).isEqualTo(7L);
        assertThat(requestCaptor.getValue().requestedScopes()).isNull();
        assertThat(requestCaptor.getValue().submittedOptionalScopes()).containsExactly("note:write");
        assertThat(requestCaptor.getValue().codeChallenge()).isEqualTo(VALUE);
        assertThat(requestCaptor.getValue().socketRemoteAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void incompleteConsentRetainsExactlyOneTenMinuteTransactionAndReturnsStandardConsentToken() {
        Fixture fixture = fixture();
        when(fixture.consentService.prepare(eq(7L), any(), any(), any())).thenReturn(requiredDecision());

        Authentication result = fixture.provider.authenticate(request(fixture.session, true,
                new LinkedHashSet<>(List.of("note:read", "note:write")), null));

        assertThat(result).isInstanceOf(OAuth2AuthorizationConsentAuthenticationToken.class);
        OAuth2AuthorizationConsentAuthenticationToken consent = (OAuth2AuthorizationConsentAuthenticationToken) result;
        assertThat(consent.getClientId()).isEqualTo("core_agent");
        assertThat(consent.getState()).isEqualTo(STATE);
        assertThat(consent.getScopes()).containsExactly("note:write");
        assertThat(consent.getPrincipal()).isInstanceOf(CoreAgentBrowserAuthenticationToken.class);
        verify(fixture.issueService, never()).issue(any());
        assertThat(fixture.session.getMaxInactiveInterval())
                .isEqualTo(HttpSessionCoreAgentAuthorizationTransactionStore.SESSION_TIMEOUT_SECONDS);

        Optional<CoreAgentBrowserAuthorizationTransaction> stored = fixture.transactions.find(fixture.session, STATE, 7L);
        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow()).satisfies(transaction -> {
            assertThat(transaction.clientId()).isEqualTo("core_agent");
            assertThat(transaction.redirectUri()).isEqualTo(REDIRECT_URI);
            assertThat(transaction.requestedScopes()).containsExactly("note:read", "note:write");
            assertThat(transaction.codeChallenge()).isEqualTo(VALUE);
            assertThat(transaction.codeChallengeMethod()).isEqualTo("S256");
            assertThat(transaction.oauthState()).isEqualTo(STATE);
            assertThat(transaction.originalSocketAddress()).isEqualTo("127.0.0.1");
            assertThat(transaction.authenticatedUserId()).isEqualTo(7L);
            assertThat(transaction.issuedAt()).isEqualTo(NOW);
            assertThat(transaction.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        });
    }

    @Test
    void rejectsUnboundClientOrRedirectWithoutAttachingAnUnsafeRedirectToken() {
        Fixture fixture = fixture();
        OAuth2AuthorizationCodeRequestAuthenticationToken wrongClient = request(fixture.session, false, null,
                "other-client");

        assertUnboundInvalid(() -> fixture.provider.authenticate(wrongClient));
        verify(fixture.consentService, never()).prepare(any(), any(), any(), any());

        OAuth2AuthorizationCodeRequestAuthenticationToken wrongRedirect = request(fixture.session, false, null,
                null, "http://attacker.example/callback");
        assertUnboundInvalid(() -> fixture.provider.authenticate(wrongRedirect));
    }

    @Test
    void preservesScopeOmissionButRejectsExplicitEmptyScopeAndInvalidPkceBeforeSideEffects() {
        Fixture fixture = fixture();
        when(fixture.consentService.prepare(eq(7L), any(), any(), eq(null))).thenReturn(reusedDecision());
        when(fixture.issueService.issue(any())).thenReturn(new IssuedCoreAgentAuthorizationCode(ISSUE_CODE,
                NOW.plus(Duration.ofMinutes(10))));

        fixture.provider.authenticate(request(fixture.session, false, null, null));

        assertBoundInvalid(() -> fixture.provider.authenticate(request(fixture.session, true, Set.of(), null)));
        assertBoundInvalid(() -> fixture.provider.authenticate(request(fixture.session, false, null, null,
                REDIRECT_URI, "not-a-256-bit-challenge", "S256")));
        assertBoundInvalid(() -> fixture.provider.authenticate(request(fixture.session, false, null, null,
                REDIRECT_URI, VALUE, "plain")));
    }

    @Test
    void requiresTheExactBrowserPrincipalAndConsistentRoleMetadata() {
        Fixture wrongPrincipal = fixture();
        OAuth2AuthorizationCodeRequestAuthenticationToken input = rawRequest(fixtureSession(wrongPrincipal),
                UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()), null, false, VALUE, "S256",
                "core_agent", REDIRECT_URI);
        input.setDetails(details(fixtureSession(wrongPrincipal), false));
        assertBoundAccessDenied(() -> wrongPrincipal.provider.authenticate(input));

        Fixture roleMismatch = fixture();
        when(roleMismatch.roleResolver.resolve(2L)).thenReturn(new EffectiveRolePermissions(2L, "ADMIN", 2,
                List.of("note:read")));
        assertThatIllegalStateException().isThrownBy(() -> roleMismatch.provider.authenticate(
                request(roleMismatch.session, false, null, null)));
        verify(roleMismatch.consentService, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void mapsIssueEligibilityRejectionToBoundAccessDeniedAndPropagatesSystemFailures() {
        Fixture rejected = fixture();
        when(rejected.consentService.prepare(eq(7L), any(), any(), eq(null))).thenReturn(reusedDecision());
        when(rejected.issueService.issue(any())).thenThrow(new com.jacolp.module.system.biz.application.authorization
                .CoreAgentAuthorizationCodeIssueRejectedException());
        assertBoundAccessDenied(() -> rejected.provider.authenticate(request(rejected.session, false, null, null)));

        Fixture broken = fixture();
        when(broken.consentService.prepare(eq(7L), any(), any(), eq(null))).thenThrow(new IllegalStateException("db down"));
        assertThatIllegalStateException().isThrownBy(() -> broken.provider.authenticate(
                request(broken.session, false, null, null))).withMessage("db down");
    }

    @Test
    void supportsOnlySasAuthorizationCodeRequestTokensAndIsConditionallyRegistered() {
        Fixture fixture = fixture();
        assertThat(fixture.provider.supports(OAuth2AuthorizationCodeRequestAuthenticationToken.class)).isTrue();
        assertThat(fixture.provider.supports(OAuth2AuthorizationConsentAuthenticationToken.class)).isFalse();
        assertThat(fixture.provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();

        runner.run(context -> assertThat(context.getBeansOfType(
                CoreAgentAuthorizationCodeRequestAuthenticationProvider.class)).isEmpty());
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(
                        CoreAgentAuthorizationCodeRequestAuthenticationProvider.class)).isEmpty());
        runner.withUserConfiguration(DependencyConfiguration.class)
                .withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(
                        CoreAgentAuthorizationCodeRequestAuthenticationProvider.class)).hasSize(1));
    }

    private static void assertUnboundInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertOAuthError(callable, "invalid_request", true);
    }

    private static void assertBoundInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertOAuthError(callable, "invalid_request", false);
    }

    private static void assertBoundAccessDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertOAuthError(callable, "access_denied", false);
    }

    private static void assertOAuthError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code,
                                         boolean noRequestToken) {
        assertThatThrownBy(callable).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class)
                .satisfies(exception -> {
                    OAuth2AuthenticationException oauth = (OAuth2AuthenticationException) exception;
                    OAuth2AuthorizationCodeRequestAuthenticationException requestException =
                            (OAuth2AuthorizationCodeRequestAuthenticationException) exception;
                    assertThat(oauth.getError().getErrorCode()).isEqualTo(code);
                    if (noRequestToken) {
                        assertThat(requestException.getAuthorizationCodeRequestAuthentication()).isNull();
                    } else {
                        assertThat(requestException.getAuthorizationCodeRequestAuthentication()).isNotNull();
                    }
                });
    }

    private static Fixture fixture() {
        CoreAgentRegisteredClientPolicyResolver policyResolver = mock(CoreAgentRegisteredClientPolicyResolver.class);
        when(policyResolver.resolve(CoreAgentRegisteredClientPolicyResolver.CORE_AGENT_CLIENT_ID)).thenReturn(policy());
        EffectiveRolePermissionResolver roleResolver = mock(EffectiveRolePermissionResolver.class);
        when(roleResolver.resolve(2L)).thenReturn(role());
        CoreAgentAuthorizationConsentService consentService = mock(CoreAgentAuthorizationConsentService.class);
        CoreAgentAuthorizationCodeIssueService issueService = mock(CoreAgentAuthorizationCodeIssueService.class);
        HttpSessionCoreAgentAuthorizationTransactionStore transactions =
                new HttpSessionCoreAgentAuthorizationTransactionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        CoreAgentAuthorizationCodeRequestAuthenticationProvider provider =
                new CoreAgentAuthorizationCodeRequestAuthenticationProvider(policyResolver, roleResolver, consentService,
                        issueService, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(provider, policyResolver, roleResolver, consentService, issueService, transactions,
                new MockHttpSession());
    }

    private static MockHttpSession fixtureSession(Fixture fixture) {
        return fixture.session;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken request(HttpSession session, boolean scopePresent,
                                                                               Set<String> scopes, String clientId) {
        return request(session, scopePresent, scopes, clientId, REDIRECT_URI);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken request(HttpSession session, boolean scopePresent,
                                                                               Set<String> scopes, String clientId,
                                                                               String redirectUri) {
        return request(session, scopePresent, scopes, clientId, redirectUri, VALUE, "S256");
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken request(HttpSession session, boolean scopePresent,
                                                                               Set<String> scopes, String clientId,
                                                                               String redirectUri, String challenge,
                                                                               String method) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = rawRequest(session,
                CoreAgentBrowserAuthenticationToken.authenticated(principal()), scopes, scopePresent, challenge, method,
                clientId == null ? "core_agent" : clientId, redirectUri);
        request.setDetails(details(session, scopePresent));
        return request;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken rawRequest(HttpSession session,
                                                                                   Authentication principal,
                                                                                   Set<String> scopes,
                                                                                   boolean scopePresent,
                                                                                   String challenge,
                                                                                   String method,
                                                                                   String clientId,
                                                                                   String redirectUri) {
        return new OAuth2AuthorizationCodeRequestAuthenticationToken("http://core-node.test/oauth2/authorize", clientId,
                principal, redirectUri, STATE, scopes,
                Map.of("code_challenge", challenge, "code_challenge_method", method));
    }

    private static CoreAgentAuthorizationEndpointRequestDetails details(HttpSession session, boolean scopePresent) {
        return new CoreAgentAuthorizationEndpointRequestDetails(session, session.getId(), "127.0.0.1", scopePresent,
                null);
    }

    private static CoreAgentAuthorizationConsentDecision reusedDecision() {
        return new CoreAgentAuthorizationConsentDecision(options(), false, List.of("note:read", "note:write"));
    }

    private static CoreAgentAuthorizationConsentDecision requiredDecision() {
        return new CoreAgentAuthorizationConsentDecision(options(), true, List.of());
    }

    private static CoreAgentConsentScopeOptions options() {
        return new CoreAgentConsentScopeOptions(List.of("note:read", "note:write"), List.of("note:read"),
                List.of("note:write"), List.of("note:write"));
    }

    private static CoreAgentRegisteredClientPolicy policy() {
        return new CoreAgentRegisteredClientPolicy("registered-core-agent", "core_agent", REDIRECT_URI,
                Set.of("note:read", "note:write"), Set.of("note:read"), "127.0.0.1/32",
                Duration.ofHours(1), Duration.ofHours(24), Duration.ofMinutes(10));
    }

    private static EffectiveRolePermissions role() {
        return new EffectiveRolePermissions(2L, "USER", 2, List.of("note:read", "note:write"));
    }

    private static CoreAgentBrowserPrincipal principal() {
        return new CoreAgentBrowserPrincipal(7L, "alice", 2L, "USER", 2);
    }

    private record Fixture(CoreAgentAuthorizationCodeRequestAuthenticationProvider provider,
                           CoreAgentRegisteredClientPolicyResolver policyResolver,
                           EffectiveRolePermissionResolver roleResolver,
                           CoreAgentAuthorizationConsentService consentService,
                           CoreAgentAuthorizationCodeIssueService issueService,
                           HttpSessionCoreAgentAuthorizationTransactionStore transactions,
                           MockHttpSession session) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() {
            return mock(CoreAgentRegisteredClientPolicyResolver.class);
        }

        @Bean EffectiveRolePermissionResolver roleResolver() {
            return mock(EffectiveRolePermissionResolver.class);
        }

        @Bean CoreAgentAuthorizationConsentService consentService() {
            return mock(CoreAgentAuthorizationConsentService.class);
        }

        @Bean CoreAgentAuthorizationCodeIssueService issueService() {
            return mock(CoreAgentAuthorizationCodeIssueService.class);
        }

        @Bean HttpSessionCoreAgentAuthorizationTransactionStore transactionStore() {
            return new HttpSessionCoreAgentAuthorizationTransactionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
