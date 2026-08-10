package com.jacolp.middleware.common.security.oauth2.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OAuth2RefreshTokenSessionServiceTest {
    private static final String RAW = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String OTHER_RAW = "AQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String JTI = "AAECAwQFBgcICQoLDA0ODw";
    private static final String OTHER_JTI = "BAECAwQFBgcICQoLDA0ODw";
    private final Instant now = Instant.parse("2026-08-10T00:00:00Z");
    private final OpaqueTokenProtector protector = new OpaqueTokenProtector();

    private OAuth2TokenStateStore stateStore;
    private OAuth2RefreshTokenSessionService service;

    @BeforeEach
    void init() {
        stateStore = mock(OAuth2TokenStateStore.class);
        service = new OAuth2RefreshTokenSessionService(Clock.fixed(now, ZoneOffset.UTC),
                new SecureOAuth2TokenGenerator(new DeterministicSecureRandom()), protector, stateStore);
    }

    @Test
    void issuesOpaqueRefreshAndOnlyPersistsProtectedState() {
        IssuedRefreshToken issued = service.issue(request(Duration.ofSeconds(60), now.plusSeconds(30)));

        ArgumentCaptor<RefreshTokenState> refreshCaptor = ArgumentCaptor.forClass(RefreshTokenState.class);
        ArgumentCaptor<OAuth2SessionState> sessionCaptor = ArgumentCaptor.forClass(OAuth2SessionState.class);
        verify(stateStore).replaceCurrentSession(refreshCaptor.capture(), sessionCaptor.capture());
        RefreshTokenState refreshState = refreshCaptor.getValue();
        OAuth2SessionState sessionState = sessionCaptor.getValue();

        assertThat(issued.rawToken()).isEqualTo(RAW);
        assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(60));
        assertThat(issued.toString()).doesNotContain(RAW).contains("<redacted>");
        assertThat(refreshState.fingerprint()).isEqualTo(protector.fingerprint(RAW)).isNotEqualTo(RAW);
        assertThat(refreshState.verifierHash()).doesNotContain(RAW);
        assertThat(refreshState.toString()).doesNotContain(RAW);
        assertThat(sessionState.currentRefreshFingerprint()).isEqualTo(refreshState.fingerprint()).isNotEqualTo(RAW);
        assertThat(sessionState.toString()).doesNotContain(RAW);
        assertThat(refreshState.userId()).isEqualTo(1);
        assertThat(refreshState.clientId()).isEqualTo("core_agent");
        assertThat(refreshState.grantedScopes()).containsExactly("note:read", "sys:read");
        assertThat(sessionState.currentAccessJti()).isEqualTo(JTI);
        assertThat(sessionState.refreshExpiresAt()).isEqualTo(refreshState.expiresAt());
    }

    @Test
    void rejectsAccessExpiryLaterThanRefreshWithoutPersistingState() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.issue(request(Duration.ofSeconds(30), now.plusSeconds(31))));
        verifyNoInteractions(stateStore);
    }

    @Test
    void requestModelsRejectInvalidRequiredValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessTokenSessionReference("bad", now));
        assertThatIllegalArgumentException().isThrownBy(() -> new RefreshTokenIssueRequest(1, "core_agent", List.of(),
                new AccessTokenSessionReference(JTI, now), Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new VerifiedRefreshToken("bad", 1, "core_agent", List.of(), now));
    }

    @Test
    void verifiesCurrentRefreshSessionWithoutReturningRawOrProfileData() {
        RefreshTokenState refreshState = refreshState(RAW, now.plusSeconds(60));
        OAuth2SessionState sessionState = session(refreshState, refreshState.fingerprint(), refreshState.expiresAt());
        when(stateStore.findRefreshByFingerprint(refreshState.fingerprint())).thenReturn(Optional.of(refreshState));
        when(stateStore.findSession("core_agent", 1)).thenReturn(Optional.of(sessionState));

        Optional<VerifiedRefreshToken> verified = service.verify(RAW);

        assertThat(verified).contains(new VerifiedRefreshToken(refreshState.fingerprint(), 1, "core_agent",
                List.of("note:read", "sys:read"), refreshState.expiresAt()));
        assertThat(verified.orElseThrow().toString()).doesNotContain(RAW).doesNotContain("username").doesNotContain("role");
        verify(stateStore).findRefreshByFingerprint(refreshState.fingerprint());
        verify(stateStore).findSession("core_agent", 1);
    }

    @Test
    void rejectsInvalidOrUnknownRawRefreshBeforeSessionLookup() {
        assertThat(service.verify("bad")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
        verifyNoInteractions(stateStore);

        String fingerprint = protector.fingerprint(RAW);
        when(stateStore.findRefreshByFingerprint(fingerprint)).thenReturn(Optional.empty());
        assertThat(service.verify(RAW)).isEmpty();
        verify(stateStore, never()).findSession("core_agent", 1);
    }

    @Test
    void rejectsExpiredAndVerifierMismatchedRefreshState() {
        RefreshTokenState expired = refreshState(RAW, now);
        when(stateStore.findRefreshByFingerprint(expired.fingerprint())).thenReturn(Optional.of(expired));
        assertThat(service.verify(RAW)).isEmpty();
        verify(stateStore, never()).findSession("core_agent", 1);

        RefreshTokenState mismatch = refreshState(OTHER_RAW, now.plusSeconds(60));
        when(stateStore.findRefreshByFingerprint(protector.fingerprint(RAW))).thenReturn(Optional.of(mismatch));
        assertThat(service.verify(RAW)).isEmpty();
        verify(stateStore, never()).findSession("core_agent", 1);
    }

    @Test
    void rejectsOrphanRefreshWhenSessionIsMissingOrNoLongerPointsToIt() {
        RefreshTokenState refreshState = refreshState(RAW, now.plusSeconds(60));
        when(stateStore.findRefreshByFingerprint(refreshState.fingerprint())).thenReturn(Optional.of(refreshState));
        when(stateStore.findSession("core_agent", 1)).thenReturn(Optional.empty());
        assertThat(service.verify(RAW)).isEmpty();

        OAuth2SessionState oldSession = session(refreshState, protector.fingerprint(OTHER_RAW), refreshState.expiresAt());
        when(stateStore.findSession("core_agent", 1)).thenReturn(Optional.of(oldSession));
        assertThat(service.verify(RAW)).isEmpty();
    }

    @Test
    void rejectsSessionWithDifferentRefreshExpiry() {
        RefreshTokenState refreshState = refreshState(RAW, now.plusSeconds(60));
        OAuth2SessionState mismatchedExpiry = session(refreshState, refreshState.fingerprint(), now.plusSeconds(59));
        when(stateStore.findRefreshByFingerprint(refreshState.fingerprint())).thenReturn(Optional.of(refreshState));
        when(stateStore.findSession("core_agent", 1)).thenReturn(Optional.of(mismatchedExpiry));

        assertThat(service.verify(RAW)).isEmpty();
    }

    @Test
    void propagatesStateStoreFailureWithoutLeakingRawRefresh() {
        String fingerprint = protector.fingerprint(RAW);
        when(stateStore.findRefreshByFingerprint(fingerprint)).thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> service.verify(RAW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
    }

    private RefreshTokenIssueRequest request(Duration refreshTtl, Instant accessExpiresAt) {
        return new RefreshTokenIssueRequest(1, "core_agent", List.of("sys:read", "note:read"),
                new AccessTokenSessionReference(JTI, accessExpiresAt), refreshTtl);
    }

    private RefreshTokenState refreshState(String rawToken, Instant expiresAt) {
        OpaqueTokenProtection protection = protector.protect(rawToken);
        return new RefreshTokenState(protection.fingerprint(), protection.verifierHash(), 1, "core_agent",
                List.of("note:read", "sys:read"), now.minusSeconds(1), expiresAt);
    }

    private OAuth2SessionState session(RefreshTokenState refreshState, String fingerprint, Instant expiresAt) {
        return new OAuth2SessionState(1, "core_agent", OTHER_JTI, now.plusSeconds(30), fingerprint, expiresAt);
    }

    private static final class DeterministicSecureRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
        }
    }
}
