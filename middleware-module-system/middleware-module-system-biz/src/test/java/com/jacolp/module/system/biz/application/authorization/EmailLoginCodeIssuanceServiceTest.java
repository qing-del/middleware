package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeIssueRequest;
import com.jacolp.module.system.biz.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeDeliveryPort;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeGenerator;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimitDecision;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeIssueRateLimiter;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeProtector;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        return new Fixture(new EmailLoginCodeIssuanceService(resolver, fingerprint, limiter, accounts, generator,
                protector, eligibility, states, delivery, properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                accounts, generator, protector, states, delivery, account);
    }

    private record Fixture(EmailLoginCodeIssuanceService service, AuthorizationAccountRepository accounts,
                           EmailLoginCodeGenerator generator, EmailLoginCodeProtector protector,
                           EmailLoginCodeStateStore states, EmailLoginCodeDeliveryPort delivery,
                           com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount account) {
    }
}
