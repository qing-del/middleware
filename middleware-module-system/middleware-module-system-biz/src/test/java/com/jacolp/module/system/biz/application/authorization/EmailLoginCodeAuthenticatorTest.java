package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.authorization.model.AuthorizationAccount;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeAuthenticationRequest;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import com.jacolp.module.system.biz.application.authorization.model.InternalAuthenticatedAccount;
import com.jacolp.module.system.biz.application.authorization.model.InternalRegisteredClientPolicy;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeFailureDecision;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeProtector;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailLoginCodeAuthenticatorTest {

    @Test
    void authenticatesOnlyAfterAtomicConsumptionThenEligibility() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        when(fixture.states.find("user", 7L)).thenReturn(Optional.of(fixture.state));
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(true);
        when(fixture.states.consume("user", 7L, fixture.state.verifierHash())).thenReturn(true);
        when(fixture.eligibility.resolve(eq(fixture.policy), eq(fixture.account))).thenReturn(fixture.cleared);

        assertThat(fixture.authenticator.authenticate(fixture.policy, request())).isEqualTo(fixture.cleared);
        verify(fixture.states).consume("user", 7L, fixture.state.verifierHash());
        verify(fixture.eligibility).resolve(fixture.policy, fixture.account);
    }

    @Test
    void wrongCodeRecordsFailureAndUsesTheUniformRejection() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
        when(fixture.states.find("user", 7L)).thenReturn(Optional.of(fixture.state));
        when(fixture.protector.matches("012345", fixture.state.verifierHash())).thenReturn(false);
        when(fixture.states.recordFailure("user", 7L, fixture.state.verifierHash(), 5))
                .thenReturn(EmailLoginCodeFailureDecision.RECORDED);

        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class);
        verify(fixture.states).recordFailure("user", 7L, fixture.state.verifierHash(), 5);
    }

    @Test
    void missingAccountExecutesOneDummyBcryptMatchThenRejects() {
        Fixture fixture = fixture();
        when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                .isInstanceOf(InternalAccountAuthenticationRejectedException.class);
        verify(fixture.protector).matches("012345", null);
    }

    @Test
    void futureAndExpiredStatesMatchOnceThenDeleteWithoutFailureOrConsumption() {
        for (EmailLoginCodeState state : new EmailLoginCodeState[]{
                state(Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plus(Duration.ofMinutes(10))),
                state(Instant.EPOCH.minus(Duration.ofMinutes(10)), Instant.EPOCH)}) {
            Fixture fixture = fixture();
            when(fixture.accounts.findByEmail("alice@example.test")).thenReturn(Optional.of(fixture.account));
            when(fixture.states.find("user", 7L)).thenReturn(Optional.of(state));
            when(fixture.protector.matches("012345", state.verifierHash())).thenReturn(false);

            assertThatThrownBy(() -> fixture.authenticator.authenticate(fixture.policy, request()))
                    .isInstanceOf(RuntimeException.class);
            verify(fixture.protector).matches("012345", state.verifierHash());
            verify(fixture.states).delete("user", 7L);
            org.mockito.Mockito.verify(fixture.states, org.mockito.Mockito.never()).recordFailure(any(), any(), any(), any());
            org.mockito.Mockito.verify(fixture.states, org.mockito.Mockito.never()).consume(any(), any(), any());
        }
    }

    private static EmailLoginCodeAuthenticationRequest request() {
        return new EmailLoginCodeAuthenticationRequest("alice@example.test", "012345");
    }

    private static Fixture fixture() {
        AuthorizationAccountRepository accounts = mock(AuthorizationAccountRepository.class);
        EmailLoginCodeProtector protector = mock(EmailLoginCodeProtector.class);
        EmailLoginCodeStateStore states = mock(EmailLoginCodeStateStore.class);
        InternalAccountEligibilityService eligibility = mock(InternalAccountEligibilityService.class);
        OAuth2EmailLoginCodeProperties properties = new OAuth2EmailLoginCodeProperties();
        InternalRegisteredClientPolicy policy = new InternalRegisteredClientPolicy("id", "user", "email-code",
                Set.of("note:read"), Set.of("note:read"), "192.0.2.0/24", Duration.ofHours(3), Duration.ofHours(72));
        AuthorizationAccount account = new AuthorizationAccount(7L, "alice", "$2a$10$" + "b".repeat(53),
                "alice@example.test", 3L, "", 1);
        String fingerprint = new EmailLoginBindingFingerprint().email("alice@example.test");
        EmailLoginCodeState state = state(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofMinutes(10)));
        InternalAuthenticatedAccount cleared = new InternalAuthenticatedAccount(7L, "alice", "alice@example.test", 3L, "USER", 3);
        return new Fixture(new EmailLoginCodeAuthenticator(accounts, protector, states, eligibility,
                new EmailLoginBindingFingerprint(), properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                accounts, protector, states, eligibility, policy, account, state, cleared);
    }

    private static EmailLoginCodeState state(Instant issuedAt, Instant expiresAt) {
        String fingerprint = new EmailLoginBindingFingerprint().email("alice@example.test");
        return new EmailLoginCodeState("user", 7L, fingerprint, "$2a$10$" + "a".repeat(53), 0, issuedAt, expiresAt);
    }

    private record Fixture(EmailLoginCodeAuthenticator authenticator, AuthorizationAccountRepository accounts,
                           EmailLoginCodeProtector protector, EmailLoginCodeStateStore states,
                           InternalAccountEligibilityService eligibility, InternalRegisteredClientPolicy policy,
                           AuthorizationAccount account, EmailLoginCodeState state, InternalAuthenticatedAccount cleared) {
    }
}
