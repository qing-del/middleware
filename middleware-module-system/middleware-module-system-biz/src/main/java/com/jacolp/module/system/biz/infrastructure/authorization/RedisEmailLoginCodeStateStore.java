package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Redis Hash adapter for strictly decoded protected email-code state. */
@Repository
public class RedisEmailLoginCodeStateStore implements EmailLoginCodeStateStore {

    private static final Set<String> CLIENTS = Set.of("user", "admin");

    private final StringRedisTemplate redis;
    private final EmailLoginCodeStateCodec codec;
    private final Clock clock;
    private final DefaultRedisScript<Long> replaceScript;

    @Autowired
    public RedisEmailLoginCodeStateStore(StringRedisTemplate redis) {
        this(redis, new EmailLoginCodeStateCodec(), Clock.systemUTC(), replaceScript());
    }

    RedisEmailLoginCodeStateStore(
            StringRedisTemplate redis,
            EmailLoginCodeStateCodec codec,
            Clock clock,
            DefaultRedisScript<Long> replaceScript) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.replaceScript = Objects.requireNonNull(replaceScript, "replaceScript");
    }

    @Override
    public Optional<EmailLoginCodeState> find(String clientId, Long userId) {
        String key = key(clientId, userId);
        Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String field) || !(entry.getValue() instanceof String value)) {
                throw invalid();
            }
            map.put(field, value);
        }
        EmailLoginCodeState state = codec.decode(map);
        if (!clientId.equals(state.clientId()) || !userId.equals(state.userId())) {
            throw invalid();
        }
        return Optional.of(state);
    }

    @Override
    public void delete(String clientId, Long userId) {
        redis.delete(key(clientId, userId));
    }

    @Override
    public void replace(EmailLoginCodeState state) {
        Objects.requireNonNull(state, "state");
        Instant now = clock.instant();
        if (state.issuedAt().isAfter(now)) {
            throw invalid();
        }
        long ttlMilliseconds = ttlMilliseconds(now, state.expiresAt());
        Map<String, String> encoded = codec.encode(state);
        List<Object> arguments = new ArrayList<>(1 + encoded.size() * 2);
        arguments.add(Long.toString(ttlMilliseconds));
        for (String fieldName : codec.fieldNames()) {
            arguments.add(fieldName);
            arguments.add(encoded.get(fieldName));
        }
        Long result = redis.execute(
                replaceScript,
                List.of(key(state.clientId(), state.userId())),
                arguments.toArray());
        if (result == null || result != 1L) {
            throw new IllegalStateException("Invalid email-code state replace result");
        }
    }

    private static long ttlMilliseconds(Instant now, Instant expiresAt) {
        try {
            long ttlMilliseconds = Duration.between(now, expiresAt).toMillis();
            if (ttlMilliseconds <= 0) {
                throw invalid();
            }
            return ttlMilliseconds;
        } catch (ArithmeticException exception) {
            throw invalid();
        }
    }

    private static DefaultRedisScript<Long> replaceScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/email_login_code_state_replace.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static String key(String clientId, Long userId) {
        if (!CLIENTS.contains(clientId) || userId == null || userId <= 0) {
            throw invalid();
        }
        return "user:email_code:" + clientId + ":" + userId;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid email-code state key or data");
    }
}
