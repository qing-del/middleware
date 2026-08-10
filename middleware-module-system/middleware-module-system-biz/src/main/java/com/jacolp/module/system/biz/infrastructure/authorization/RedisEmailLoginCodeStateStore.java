package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

/** Redis Hash adapter for strictly decoded protected email-code state. */
@Repository
public class RedisEmailLoginCodeStateStore implements EmailLoginCodeStateStore {
    private static final Set<String> CLIENTS = Set.of("user", "admin");
    private final StringRedisTemplate redis;
    private final EmailLoginCodeStateCodec codec;
    @Autowired
    public RedisEmailLoginCodeStateStore(StringRedisTemplate redis) {
        this(redis, new EmailLoginCodeStateCodec());
    }
    RedisEmailLoginCodeStateStore(StringRedisTemplate redis, EmailLoginCodeStateCodec codec) { this.redis = Objects.requireNonNull(redis); this.codec = Objects.requireNonNull(codec); }
    @Override public Optional<EmailLoginCodeState> find(String clientId, Long userId) {
        String key = key(clientId, userId); Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values == null || values.isEmpty()) return Optional.empty(); Map<String, String> map = new LinkedHashMap<>();
        for (var entry : values.entrySet()) { if (!(entry.getKey() instanceof String k) || !(entry.getValue() instanceof String v)) throw invalid(); map.put(k, v); }
        EmailLoginCodeState state = codec.decode(map);
        if (!clientId.equals(state.clientId()) || !userId.equals(state.userId())) throw invalid();
        return Optional.of(state);
    }
    @Override public void delete(String clientId, Long userId) { redis.delete(key(clientId, userId)); }
    private static String key(String clientId, Long userId) { if (!CLIENTS.contains(clientId) || userId == null || userId <= 0) throw invalid(); return "user:email_code:" + clientId + ":" + userId; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid email-code state key or data"); }
}
