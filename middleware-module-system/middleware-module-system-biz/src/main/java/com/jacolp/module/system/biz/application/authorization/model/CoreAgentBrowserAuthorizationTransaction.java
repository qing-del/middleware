package com.jacolp.module.system.biz.application.authorization.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Immutable browser-only authorization state retained while a signed-in user reviews consent.
 *
 * <p>This is deliberately not an authorization code, token, or account snapshot. It only binds a
 * validated browser authorization request to its authenticated user until consent is submitted.</p>
 */
public record CoreAgentBrowserAuthorizationTransaction(
        String clientId,
        String redirectUri,
        List<String> requestedScopes,
        String codeChallenge,
        String codeChallengeMethod,
        String oauthState,
        String originalSocketAddress,
        long authenticatedUserId,
        Instant issuedAt,
        Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

    public CoreAgentBrowserAuthorizationTransaction {
        if (!CoreAgentAuthorizationCodeState.CORE_AGENT_CLIENT_ID.equals(clientId)) {
            throw invalid("clientId must be core_agent");
        }
        redirectUri = CoreAgentAuthorizationCodeState.requireSafeRedirectUri(redirectUri);
        requestedScopes = requestedScopes == null ? null : CoreAgentAuthorizationCodeState.normalizedScopes(requestedScopes);
        CoreAgentAuthorizationCodeState.require256BitBase64Url(codeChallenge, "codeChallenge");
        if (!CoreAgentAuthorizationCodeState.PKCE_S256.equals(codeChallengeMethod)) {
            throw invalid("codeChallengeMethod must be S256");
        }
        CoreAgentAuthorizationCodeState.requireSafeState(oauthState);
        originalSocketAddress = CoreAgentAuthorizationCodeState.requireOriginalSocketLiteral(originalSocketAddress);
        if (authenticatedUserId <= 0) {
            throw invalid("authenticatedUserId must be positive");
        }
        if (issuedAt == null || expiresAt == null || !issuedAt.plus(TIME_TO_LIVE).equals(expiresAt)) {
            throw invalid("issuedAt and expiresAt must be exactly ten minutes apart");
        }
    }

    @Override
    public String toString() {
        return "CoreAgentBrowserAuthorizationTransaction[clientId=" + clientId + ", redirectUri=" + redirectUri
                + ", requestedScopes=" + requestedScopes + ", codeChallenge=<redacted>, codeChallengeMethod="
                + codeChallengeMethod + ", oauthState=<redacted>, originalSocketAddress=<redacted>"
                + ", authenticatedUserId=" + authenticatedUserId + ", issuedAt=" + issuedAt + ", expiresAt="
                + expiresAt + ']';
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid CORE AGENT browser authorization transaction: " + message);
    }
}
