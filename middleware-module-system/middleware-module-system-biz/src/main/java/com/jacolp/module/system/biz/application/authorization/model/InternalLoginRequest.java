package com.jacolp.module.system.biz.application.authorization.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** Strict, redacted request model for USER/ADMIN internal login. */
public record InternalLoginRequest(
        String clientId, String grantType, String username, String rawPassword,
        String email, String rawEmailCode, Set<String> requestedScopes, String socketRemoteAddress) {
    public InternalLoginRequest {
        if ((!"user".equals(clientId) && !"admin".equals(clientId))
                || (!"password".equals(grantType) && !"email-code".equals(grantType))
                || socketRemoteAddress == null || socketRemoteAddress.isBlank()) {
            throw new IllegalArgumentException("Invalid internal login request");
        }
        if ("password".equals(grantType)) {
            if (!text(username) || !text(rawPassword) || username.length() > 100
                    || email != null || rawEmailCode != null) {
                throw new IllegalArgumentException("Invalid internal login request");
            }
        } else {
            new EmailLoginCodeAuthenticationRequest(email, rawEmailCode);
            if (username != null || rawPassword != null) {
                throw new IllegalArgumentException("Invalid internal login request");
            }
        }
        if (requestedScopes != null) {
            LinkedHashSet<String> scopes = new LinkedHashSet<>();
            for (String scope : requestedScopes) {
                if (!text(scope) || !scopes.add(scope)) {
                    throw new IllegalArgumentException("Invalid internal login request");
                }
            }
            requestedScopes = Set.copyOf(scopes);
        }
    }

    @Override
    public String toString() {
        return "InternalLoginRequest[clientId=" + clientId + ", grantType=" + grantType
                + ", requestedScopes=" + (requestedScopes == null ? "omitted" : requestedScopes.size()) + ']';
    }
    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
