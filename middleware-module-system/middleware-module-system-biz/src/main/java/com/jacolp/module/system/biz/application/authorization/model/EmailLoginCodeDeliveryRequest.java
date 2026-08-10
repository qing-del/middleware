package com.jacolp.module.system.biz.application.authorization.model;

import java.time.Duration;
import java.util.regex.Pattern;

/** Validated raw email-login code delivery input. Never log this record. */
public record EmailLoginCodeDeliveryRequest(
        String clientId,
        Long userId,
        String email,
        String username,
        String rawCode,
        Duration ttl) {

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    public EmailLoginCodeDeliveryRequest {
        if ((!"user".equals(clientId) && !"admin".equals(clientId))
                || userId == null || userId <= 0
                || email == null || email.isBlank() || email.length() > 100
                || username == null || username.isBlank() || username.length() > 100
                || rawCode == null || !CODE.matcher(rawCode).matches()
                || ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Invalid email-code delivery request");
        }
        try {
            long ttlMilliseconds = ttl.toMillis();
            if (ttlMilliseconds <= 0 || ttlMilliseconds % 60_000L != 0) {
                throw new IllegalArgumentException("Invalid email-code delivery request");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid email-code delivery request", exception);
        }
    }

    @Override
    public String toString() {
        return "EmailLoginCodeDeliveryRequest[clientId=" + clientId + ", userId=" + userId
                + ", username=" + username + ", ttl=" + ttl + ']';
    }
}
