package com.jacolp.middleware.common.security.oauth2.token;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Read/delete adapter; atomic issuance and rotation are deliberately separate. */
public final class RedisOAuth2TokenStateStore implements OAuth2TokenStateStore {
    private static final Pattern FP = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private final StringRedisTemplate redis;
    private final OAuth2TokenStateCodec codec;

    public RedisOAuth2TokenStateStore(StringRedisTemplate redis, OAuth2TokenStateCodec codec) {
        this.redis = Objects.requireNonNull(redis);
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    public Optional<RefreshTokenState> findRefreshByFingerprint(String fingerprint) {
        validateFingerprint(fingerprint);
        Map<String, String> values = entries(refreshKey(fingerprint));
        if (values.isEmpty()) return Optional.empty();
        RefreshTokenState state = codec.decodeRefresh(values);
        if (!fingerprint.equals(state.fingerprint())) throw new IllegalArgumentException("refresh state key mismatch");
        return Optional.of(state);
    }

    @Override
    public Optional<OAuth2SessionState> findSession(String clientId, long userId) {
        validateSessionInput(clientId, userId);
        Map<String, String> values = entries(sessionKey(clientId, userId));
        if (values.isEmpty()) return Optional.empty();
        OAuth2SessionState state = codec.decodeSession(values);
        if (!clientId.equals(state.clientId()) || userId != state.userId()) throw new IllegalArgumentException("session state key mismatch");
        return Optional.of(state);
    }

    @Override public void deleteRefresh(String fingerprint) { validateFingerprint(fingerprint); redis.delete(refreshKey(fingerprint)); }
    @Override public void deleteSession(String clientId, long userId) { validateSessionInput(clientId, userId); redis.delete(sessionKey(clientId, userId)); }

    private Map<String, String> entries(String key) {
        HashOperations<String, Object, Object> hash = redis.opsForHash();
        Map<Object, Object> source = hash.entries(key);
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((field, value) -> {
            if (!(field instanceof String) || !(value instanceof String)) throw new IllegalArgumentException("invalid OAuth2 token state hash");
            result.put((String) field, (String) value);
        });
        return result;
    }

    private static String refreshKey(String fingerprint) { return "user:refresh:" + fingerprint; }
    private static String sessionKey(String clientId, long userId) { return "user:session:" + clientId + ":" + userId; }
    private static void validateFingerprint(String value) { if (value == null || !FP.matcher(value).matches()) throw new IllegalArgumentException("fingerprint must be Base64URL"); }
    private static void validateSessionInput(String clientId, long userId) { if (userId <= 0 || clientId == null || !CLIENT.matcher(clientId).matches()) throw new IllegalArgumentException("invalid session key input"); }
}
