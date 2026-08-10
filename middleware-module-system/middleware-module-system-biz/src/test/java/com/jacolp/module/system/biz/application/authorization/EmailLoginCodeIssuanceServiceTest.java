package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.module.system.biz.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeDeliveryPort;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeGenerator;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimiter;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeProtector;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class EmailLoginCodeIssuanceServiceTest {

    @Test
    void rateDeniedDoesNotLookupOrGenerate() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.COOLDOWN);

        fixture.service.issue(request());

        verify(fixture.accounts, never()).findByEmail(any());
        verify(fixture.generator, never()).generate();
        verify(fixture.protector, never()).protect(any());
    }

    @Test
    void windowLimitAlsoDoesNotLookupOrGenerateAndNullDecisionFailsClosed() {
        Fixture limited = fixture(EmailLoginCodeIssueRateLimitDecision.WINDOW_LIMIT);
        limited.service.issue(request());
        verify(limited.accounts, never()).findByEmail(any());
        verify(limited.generator, never()).generate();

        Fixture malformed = fixture(null);
        assertThatThrownBy(() -> malformed.service.issue(request())).isInstanceOf(IllegalStateException.class);
        verify(malformed.accounts, never()).findByEmail(any());
    }

    @Test
    void missingAccountStillGeneratesAndProtectsOnceButDoesNotStoreOrDeliver() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.empty());

        fixture.service.issue(request());

        verify(fixture.generator).generate();
        verify(fixture.protector).protect("012345");
        verify(fixture.states, never()).replace(any());
        verify(fixture.delivery, never()).deliver(any());
    }

    @Test
    void smtpFailureDeletesStateAndRethrows() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        IllegalStateException failure = new IllegalStateException("smtp");
        org.mockito.Mockito.doThrow(failure).when(fixture.delivery).deliver(any());

        assertThatThrownBy(() -> fixture.service.issue(request())).isSameAs(failure);
        verify(fixture.states).replace(any());
        verify(fixture.states).delete("user", 7L);
    }

    @Test
    void replaceFailureDoesNotDeliverOrDeleteAndCleanupFailureSuppressesSmtpFailure() {
        Fixture replaceFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(replaceFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(replaceFailure.account));
        IllegalStateException stateFailure = new IllegalStateException("state");
        org.mockito.Mockito.doThrow(stateFailure).when(replaceFailure.states).replace(any());
        assertThatThrownBy(() -> replaceFailure.service.issue(request())).isSameAs(stateFailure);
        verify(replaceFailure.delivery, never()).deliver(any());
        verify(replaceFailure.states, never()).delete(any(), any());

        Fixture cleanupFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(cleanupFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(cleanupFailure.account));
        IllegalStateException smtpFailure = new IllegalStateException("smtp");
        IllegalStateException deleteFailure = new IllegalStateException("delete");
        org.mockito.Mockito.doThrow(smtpFailure).when(cleanupFailure.delivery).deliver(any());
        org.mockito.Mockito.doThrow(deleteFailure).when(cleanupFailure.states).delete("user", 7L);
        assertThatThrownBy(() -> cleanupFailure.service.issue(request()))
                .isSameAs(deleteFailure)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(error.getSuppressed()).containsExactly(smtpFailure));
    }

    @Test
    void ipAndResolverRejectionsStopBeforeRateAndLookup() {
        Fixture ipDenied = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        InternalRegisteredClientPolicy deniedPolicy = new InternalRegisteredClientPolicy("id", "user", "email-code",
                Set.of("note:read"), Set.of("note:read"), "198.51.100.0/24", Duration.ofHours(3), Duration.ofHours(72));
        when(ipDenied.resolver.resolve("user", "email-code")).thenReturn(deniedPolicy);
        assertThatThrownBy(() -> ipDenied.service.issue(request())).isInstanceOf(EmailLoginCodeIssuanceRejectedException.class);
        verify(ipDenied.limiter, never()).tryAcquire(any());
        verify(ipDenied.generator, never()).generate();

        Fixture resolverFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException failure = new IllegalStateException("resolver");
        when(resolverFailure.resolver.resolve("user", "email-code")).thenThrow(failure);
        assertThatThrownBy(() -> resolverFailure.service.issue(request())).isSameAs(failure);
        verify(resolverFailure.limiter, never()).tryAcquire(any());
    }

    @Test
    void emailMismatchAndEligibilityRejectionDoNotStoreOrDeliver() {
        Fixture mismatch = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount wrongEmail =
                new com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), "other@example.test", 3L, "", 1);
        when(mismatch.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(wrongEmail));
        mismatch.service.issue(request());
        verify(mismatch.generator).generate();
        verify(mismatch.protector).protect("012345");
        verify(mismatch.eligibility, never()).resolve(any(), any());
        verify(mismatch.states, never()).replace(any());

        Fixture rejected = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(rejected.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(rejected.account));
        when(rejected.eligibility.resolve(any(), any())).thenThrow(new InternalAccountAuthenticationRejectedException());
        rejected.service.issue(request());
        verify(rejected.states, never()).replace(any());
        verify(rejected.delivery, never()).deliver(any());
    }

    @Test
    void nullLookupAndNullAccountEmailFailClosedAfterProtecting() {
        Fixture nullLookup = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(nullLookup.accounts.findByEmail("alice@example.test")).thenReturn(null);
        assertThatThrownBy(() -> nullLookup.service.issue(request())).isInstanceOf(IllegalStateException.class);
        verify(nullLookup.generator).generate();
        verify(nullLookup.protector).protect("012345");
        verify(nullLookup.states, never()).replace(any());

        Fixture nullEmail = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount account =
                new com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), null, 3L, "", 1);
        when(nullEmail.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(account));
        nullEmail.service.issue(request());
        verify(nullEmail.eligibility, never()).resolve(any(), any());
        verify(nullEmail.states, never()).replace(any());
        verify(nullEmail.delivery, never()).deliver(any());
    }

    @Test
    void invalidClearedIdentitiesFailClosedWithoutStateOrDelivery() {
        for (com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount cleared :
                new com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount[]{
                        null,
                        new com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount(
                                8L, "alice", "alice@example.test", 3L, "USER", 3),
                        new com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount(
                                7L, "alice", "other@example.test", 3L, "USER", 3)}) {
            Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
            when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
            when(fixture.eligibility.resolve(any(), any())).thenReturn(cleared);
            assertThatThrownBy(() -> fixture.service.issue(request())).isInstanceOf(IllegalStateException.class);
            verify(fixture.states, never()).replace(any());
            verify(fixture.delivery, never()).deliver(any());
        }
    }

    @Test
    void dependencyFailuresPropagateWithoutLaterSideEffectsAndDeliveryErrorCleansUp() {
        Fixture limiterFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        IllegalStateException limiterError = new IllegalStateException("limiter");
        when(limiterFailure.limiter.tryAcquire(any())).thenThrow(limiterError);
        assertThatThrownBy(() -> limiterFailure.service.issue(request())).isSameAs(limiterError);
        verify(limiterFailure.generator, never()).generate();

        Fixture eligibilityFailure = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(eligibilityFailure.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(eligibilityFailure.account));
        IllegalStateException eligibilityError = new IllegalStateException("eligibility");
        when(eligibilityFailure.eligibility.resolve(any(), any())).thenThrow(eligibilityError);
        assertThatThrownBy(() -> eligibilityFailure.service.issue(request())).isSameAs(eligibilityError);
        verify(eligibilityFailure.states, never()).replace(any());

        Fixture errorDelivery = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(errorDelivery.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(errorDelivery.account));
        AssertionError error = new AssertionError("smtp");
        org.mockito.Mockito.doThrow(error).when(errorDelivery.delivery).deliver(any());
        assertThatThrownBy(() -> errorDelivery.service.issue(request())).isSameAs(error);
        verify(errorDelivery.states).delete("user", 7L);
    }

    @Test
    void validIssuePersistsAndDeliversTheClearedIdentityInOrder() {
        Fixture fixture = fixture(EmailLoginCodeIssueRateLimitDecision.ALLOWED);
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        InternalAuthenticatedAccount cleared = new InternalAuthenticatedAccount(
                7L, "Cleared", "Alice@Example.Test", 3L, "USER", 3);
        when(fixture.eligibility.resolve(any(), any())).thenReturn(cleared);
        ArgumentCaptor<EmailLoginCodeState> state = ArgumentCaptor.forClass(EmailLoginCodeState.class);
        ArgumentCaptor<com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeDeliveryRequest> delivery =
                ArgumentCaptor.forClass(com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeDeliveryRequest.class);

        fixture.service.issue(request());

        verify(fixture.states).replace(state.capture());
        verify(fixture.delivery).deliver(delivery.capture());
        assertThat(state.getValue().userId()).isEqualTo(7L);
        assertThat(state.getValue().failedAttempts()).isZero();
        assertThat(state.getValue().issuedAt()).isEqualTo(Instant.EPOCH);
        assertThat(state.getValue().expiresAt()).isEqualTo(Instant.EPOCH.plus(Duration.ofMinutes(10)));
        assertThat(state.getValue().toString()).doesNotContain("alice", "012345", "$2a$");
        assertThat(delivery.getValue().email()).isEqualTo("Alice@Example.Test");
        assertThat(delivery.getValue().username()).isEqualTo("Cleared");
        assertThat(delivery.getValue().rawCode()).isEqualTo("012345");
        assertThat(delivery.getValue().ttl()).isEqualTo(Duration.ofMinutes(10));
        InOrder order = inOrder(fixture.resolver, fixture.limiter, fixture.generator, fixture.protector,
                fixture.accounts, fixture.eligibility, fixture.states, fixture.delivery);
        order.verify(fixture.resolver).resolve("user", "email-code");
        order.verify(fixture.limiter).tryAcquire(any());
        order.verify(fixture.generator).generate();
        order.verify(fixture.protector).protect("012345");
        order.verify(fixture.accounts).findByEmail("alice@example.test");
        order.verify(fixture.eligibility).resolve(any(), any());
        order.verify(fixture.states).replace(any());
        order.verify(fixture.delivery).deliver(any());
    }


    private static EmailLoginCodeIssueRequest request() {
        return new EmailLoginCodeIssueRequest("user", "alice@example.test", "192.0.2.1");
    }

    private static Fixture fixture(EmailLoginCodeIssueRateLimitDecision decision) {
        InternalRegisteredClientPolicyResolver resolver = mock(InternalRegisteredClientPolicyResolver.class);
        EmailLoginBindingFingerprint fingerprint = new EmailLoginBindingFingerprint();
        EmailLoginCodeIssueRateLimiter limiter = mock(EmailLoginCodeIssueRateLimiter.class);
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EmailLoginCodeGenerator generator = mock(EmailLoginCodeGenerator.class);
        EmailLoginCodeProtector protector = mock(EmailLoginCodeProtector.class);
        InternalAccountEligibilityService eligibility = mock(InternalAccountEligibilityService.class);
        EmailLoginCodeStateStore states = mock(EmailLoginCodeStateStore.class);
        EmailLoginCodeDeliveryPort delivery = mock(EmailLoginCodeDeliveryPort.class);
        OAuth2EmailLoginCodeProperties properties = new OAuth2EmailLoginCodeProperties();
        InternalRegisteredClientPolicy policy = new InternalRegisteredClientPolicy("id", "user", "email-code",
                Set.of("note:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(3), Duration.ofHours(72));
        when(resolver.resolve("user", "email-code")).thenReturn(policy);
        when(limiter.tryAcquire(any())).thenReturn(decision);
        when(generator.generate()).thenReturn("012345");
        when(protector.protect("012345")).thenReturn("$2a$10$" + "a".repeat(53));
        com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount account =
                new com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount(
                        7L, "alice", "$2a$10$" + "b".repeat(53), "alice@example.test", 3L, "", 1);
        when(eligibility.resolve(eq(policy), eq(account))).thenReturn(
                new com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount(
                        7L, "alice", "alice@example.test", 3L, "USER", 3));
        return new Fixture(new EmailLoginCodeIssuanceService(resolver, fingerprint, limiter, accounts, generator,
                protector, eligibility, states, delivery, properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                resolver, limiter, accounts, generator, protector, eligibility, states, delivery, account);
    }

    private record Fixture(EmailLoginCodeIssuanceService service, InternalRegisteredClientPolicyResolver resolver,
                           EmailLoginCodeIssueRateLimiter limiter, AuthorizationAccountRepository accounts,
                           EmailLoginCodeGenerator generator, EmailLoginCodeProtector protector,
                           InternalAccountEligibilityService eligibility, EmailLoginCodeStateStore states,
                           EmailLoginCodeDeliveryPort delivery,
                           com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount account) {
    }
}
