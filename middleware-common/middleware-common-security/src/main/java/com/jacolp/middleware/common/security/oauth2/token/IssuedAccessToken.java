package com.jacolp.middleware.common.security.oauth2.token;

import java.time.Instant;

/** The encoded access token together with its non-secret issue metadata. */
public record IssuedAccessToken(String tokenValue, String jti, Instant issuedAt, Instant expiresAt) {
}
