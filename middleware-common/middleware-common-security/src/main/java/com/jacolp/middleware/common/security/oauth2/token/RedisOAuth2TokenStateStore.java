package com.jacolp.middleware.common.security.oauth2.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Read/delete adapter; atomic issuance and rotation are deliberately separate. */
public final class RedisOAuth2TokenStateStore implements OAuth2TokenStateStore {
    private static final Pattern FP = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern CLIENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final StringRedisTemplate redis; private final OAuth2TokenStateCodec codec;
    public RedisOAuth2TokenStateStore(StringRedisTemplate redis, OAuth2TokenStateCodec codec) { this.redis = java.util.Objects.requireNonNull(redis); this.codec = java.util.Objects.requireNonNull(codec); }
    public Optional<RefreshTokenState> findRefreshByFingerprint(String fingerprint) {
        validateFingerprint(fingerprint); Map<String,String> values = entries(refreshKey(fingerprint)); if (values.isEmpty()) return Optional.empty();
        RefreshTokenState state = codec.decodeRefresh(values); if (!fingerprint.equals(state.fingerprint())) throw new IllegalArgumentException("refresh state key mismatch"); return Optional.of(state);
    }
    public Optional<OAuth2SessionState> findSession(String clientId, long userId) {
        validateSessionInput(clientId,userId); Map<String,String> values = entries(sessionKey(clientId,userId)); if (values.isEmpty()) return Optional.empty();
        OAuth2SessionState state = codec.decodeSession(values); if (!clientId.equals(state.clientId()) || userId != state.userId()) throw new IllegalArgumentException("session state key mismatch"); return Optional.of(state);
    }
    public void deleteRefresh(String fingerprint) { validateFingerprint(fingerprint); redis.delete(refreshKey(fingerprint)); }
    public void deleteSession(String clientId,long userId) { validateSessionInput(clientId,userId); redis.delete(sessionKey(clientId,userId)); }
    private Map<String,String> entries(String key) { Map<Object,Object> source = redis.opsForHash().entries(key); Map<String,String> result = new LinkedHashMap<>(); source.forEach((k,v)-> { if (!(k instanceof String) || !(v instanceof String)) throw new IllegalArgumentException("invalid OAuth2 token state hash"); result.put((String)k,(String)v); }); return result; }
    private static String refreshKey(String fp) { return "user:refresh:"+fp; } private static String sessionKey(String c,long u) { return "user:session:"+c+":"+u; }
    private static void validateFingerprint(String value) { if (value==null || !FP.matcher(value).matches()) throw new IllegalArgumentException("fingerprint must be Base64URL"); }
    private static void validateSessionInput(String client,long user) { if (user<=0 || client==null || !CLIENT.matcher(client).matches()) throw new IllegalArgumentException("invalid session key input"); }
}
