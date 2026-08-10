package com.jacolp.middleware.common.security.oauth2.token;

import java.time.Instant;
import java.util.Objects;

/** Delivers a raw refresh token only to the caller; logging is deliberately redacted. */
public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
    public IssuedRefreshToken {
        rawToken = Objects.requireNonNull(rawToken, "rawToken must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[rawToken=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
