package com.jacolp.middleware.common.security.oauth2.token;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Creates and verifies refresh session state without retaining raw credentials or authorization profile data. */
public final class OAuth2RefreshTokenSessionService {
    private final Clock clock;
    private final SecureOAuth2TokenGenerator tokenGenerator;
    private final OpaqueTokenProtector tokenProtector;
    private final OAuth2TokenStateStore stateStore;

    public OAuth2RefreshTokenSessionService(Clock clock, SecureOAuth2TokenGenerator tokenGenerator,
                                            OpaqueTokenProtector tokenProtector, OAuth2TokenStateStore stateStore) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        this.tokenProtector = Objects.requireNonNull(tokenProtector, "tokenProtector must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    public IssuedRefreshToken issue(RefreshTokenIssueRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Instant issuedAt = clock.instant();
        Instant refreshExpiresAt = issuedAt.plus(request.refreshTtl());
        if (request.accessToken().expiresAt().isAfter(refreshExpiresAt)) {
            throw new IllegalArgumentException("access expiry must not exceed refresh expiry");
        }

        String rawToken = tokenGenerator.newOpaqueToken();
        OpaqueTokenProtection protection = tokenProtector.protect(rawToken);
        RefreshTokenState refreshState = new RefreshTokenState(protection.fingerprint(), protection.verifierHash(),
                request.userId(), request.clientId(), request.grantedScopes(), issuedAt, refreshExpiresAt);
        OAuth2SessionState sessionState = new OAuth2SessionState(request.userId(), request.clientId(),
                request.accessToken().jti(), request.accessToken().expiresAt(), protection.fingerprint(), refreshExpiresAt);
        stateStore.replaceCurrentSession(refreshState, sessionState);
        return new IssuedRefreshToken(rawToken, refreshExpiresAt);
    }

    public Optional<VerifiedRefreshToken> verify(String rawToken) {
        final String fingerprint;
        try {
            fingerprint = tokenProtector.fingerprint(rawToken);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        Optional<RefreshTokenState> state = stateStore.findRefreshByFingerprint(fingerprint);
        if (state.isEmpty() || !state.get().expiresAt().isAfter(clock.instant())) return Optional.empty();
        RefreshTokenState refreshState = state.get();
        if (!tokenProtector.matches(rawToken, refreshState.verifierHash())) return Optional.empty();

        Optional<OAuth2SessionState> session = stateStore.findSession(refreshState.clientId(), refreshState.userId());
        if (session.isEmpty() || !matchesCurrentSession(refreshState, session.get())) return Optional.empty();
        return Optional.of(new VerifiedRefreshToken(refreshState.fingerprint(), refreshState.userId(), refreshState.clientId(),
                refreshState.grantedScopes(), refreshState.expiresAt()));
    }

    private static boolean matchesCurrentSession(RefreshTokenState refreshState, OAuth2SessionState sessionState) {
        return refreshState.userId() == sessionState.userId()
                && refreshState.clientId().equals(sessionState.clientId())
                && refreshState.fingerprint().equals(sessionState.currentRefreshFingerprint())
                && refreshState.expiresAt().equals(sessionState.refreshExpiresAt());
    }
}
