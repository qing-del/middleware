package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.application.authorization.CoreAgentAuthorizationCodeStateCodec;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationAccountSnapshot;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentAuthorizationCodeState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisCoreAgentAuthorizationCodeStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-11T04:00:00Z");
    private static final String RAW_CODE = code((byte) 7);
    private static final String OLD_CODE = code((byte) 8);
    private static final long USER_ID = 7L;

    @Test
    void replaceUsesExactTwoKeysTtlAndFixedCodecFieldsWithoutRawCodeInTheHash() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> replace = new DefaultRedisScript<>();
        CoreAgentAuthorizationCodeState state = state(RAW_CODE, NOW.minusMillis(100));
        Object[] expectedArguments = replaceArguments(state, "599900");
        when(redis.execute(eq(replace), eq(List.of(codeKey(RAW_CODE), userKey())), eq(expectedArguments))).thenReturn(1L);

        assertThat(store(redis, replace, new DefaultRedisScript<>(), new DefaultRedisScript<>(), NOW)
                .replaceCurrent(state).rawCode()).isEqualTo(RAW_CODE);

        verify(redis).execute(eq(replace), eq(List.of(codeKey(RAW_CODE), userKey())), eq(expectedArguments));
        assertThat(expectedArguments).containsExactly("599900", RAW_CODE, "7",
                "schema_version", "1", "client_id", "core_agent", "redirect_uri", "http://127.0.0.1:9090/oauth/callback",
                "scopes", "note:read,sys:read", "code_challenge", code((byte) 9), "code_challenge_method", "S256",
                "original_socket_address", "127.0.0.1", "oauth_state", "opaque-state", "issued_at_epoch_millis",
                Long.toString(NOW.minusMillis(100).toEpochMilli()), "expires_at_epoch_millis",
                Long.toString(NOW.plus(Duration.ofMinutes(10)).minusMillis(100).toEpochMilli()), "user_id", "7",
                "username", "alice", "role_id", "2", "password_hash", "$2a$10$" + "a".repeat(53), "email_present", "0",
                "email", "", "extra_grant_types", "agent_client", "status", "0");
        assertThat(new CoreAgentAuthorizationCodeStateCodec().encode(state)).doesNotContainValue(RAW_CODE);
    }

    @Test
    void replaceRejectsExpiredFutureIssuedAndOverflowBeforeRedisAndPropagatesFailures() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), NOW);
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrent(
                state(RAW_CODE, NOW.minus(Duration.ofMinutes(10)))));
        assertThatIllegalArgumentException().isThrownBy(() -> store.replaceCurrent(state(RAW_CODE, NOW.plusMillis(1))));
        RedisCoreAgentAuthorizationCodeStore overflow = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), Instant.MIN);
        assertThatIllegalArgumentException().isThrownBy(() -> overflow.replaceCurrent(
                state(RAW_CODE, Instant.MAX.minus(Duration.ofMinutes(10)))));
        verifyNoInteractions(redis);

        DefaultRedisScript<Long> replace = new DefaultRedisScript<>();
        IllegalStateException failure = new IllegalStateException("redis unavailable");
        when(redis.execute(eq(replace), anyList(), any(Object[].class))).thenThrow(failure);
        assertThatThrownBy(() -> store(redis, replace, new DefaultRedisScript<>(), new DefaultRedisScript<>(), NOW)
                .replaceCurrent(state(RAW_CODE, NOW))).isSameAs(failure);
    }

    @Test
    void findRoundTripsTheExactCodeHashAndFailsClosedForPollutionOrNonStrings() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        CoreAgentAuthorizationCodeState state = state(RAW_CODE, NOW);
        Map<Object, Object> entries = new LinkedHashMap<>(new CoreAgentAuthorizationCodeStateCodec().encode(state));
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(entries);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                new DefaultRedisScript<>(), NOW);

        assertThat(store.findByCode(RAW_CODE)).contains(state);
        verify(hashes).entries(codeKey(RAW_CODE));

        when(hashes.entries(codeKey(OLD_CODE))).thenReturn(Map.of());
        assertThat(store.findByCode(OLD_CODE)).isEmpty();

        Map<Object, Object> polluted = new LinkedHashMap<>(entries);
        polluted.put("raw_code", RAW_CODE);
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(polluted);
        assertThatIllegalArgumentException().isThrownBy(() -> store.findByCode(RAW_CODE));
        Map<Object, Object> nonString = new LinkedHashMap<>(entries);
        nonString.put("status", 0);
        when(hashes.entries(codeKey(RAW_CODE))).thenReturn(nonString);
        assertThatIllegalArgumentException().isThrownBy(() -> store.findByCode(RAW_CODE));
    }

    @Test
    void consumeUsesPointerCasSoTwoValidAttemptsProduceExactlyOneWinner() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> consume = new DefaultRedisScript<>();
        when(redis.execute(eq(consume), eq(List.of(codeKey(RAW_CODE), userKey())), eq(RAW_CODE), eq("7")))
                .thenReturn(1L, 0L);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), consume,
                new DefaultRedisScript<>(), NOW);

        assertThat(store.consume(RAW_CODE, USER_ID)).isTrue();
        assertThat(store.consume(RAW_CODE, USER_ID)).isFalse();
        verify(redis, times(2)).execute(eq(consume), eq(List.of(codeKey(RAW_CODE), userKey())), eq(RAW_CODE), eq("7"));
        assertThatIllegalStateException().isThrownBy(() -> {
            when(redis.execute(eq(consume), eq(List.of(codeKey(OLD_CODE), userKey())), eq(OLD_CODE), eq("7")))
                    .thenReturn(-1L);
            store.consume(OLD_CODE, USER_ID);
        });
    }

    @Test
    void invalidateIsIdempotentAndUsesTheExactPointerKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DefaultRedisScript<Long> invalidate = new DefaultRedisScript<>();
        when(redis.execute(eq(invalidate), eq(List.of(userKey())), eq("7"), eq("oauth2:auth_code:"))).thenReturn(1L, 1L);
        RedisCoreAgentAuthorizationCodeStore store = store(redis, new DefaultRedisScript<>(), new DefaultRedisScript<>(),
                invalidate, NOW);

        store.invalidateCurrent(USER_ID);
        store.invalidateCurrent(USER_ID);

        verify(redis, times(2)).execute(eq(invalidate), eq(List.of(userKey())), eq("7"), eq("oauth2:auth_code:"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.invalidateCurrent(0L));
    }

    @Test
    void threeLuaScriptsValidateBeforeAnyMutationAndKeepRawDataOutOfHashFields() throws IOException {
        String replace = lua("lua/core_agent_authorization_code_replace.lua");
        assertThat(replace).contains("#KEYS ~= 2", "#ARGV ~= 39", "oauth2:auth_code:{", "user:auth_code:{",
                "local old_code", "HSET", "PEXPIRE", "SET", "schema_version", "password_hash", "email_present",
                "return 1");
        assertThat(replace.indexOf("if #KEYS ~= 2")).isLessThan(replace.indexOf("redis.call('GET'"));
        assertThat(replace.indexOf("for index, field")).isLessThan(replace.indexOf("redis.call('DEL'"));
        assertThat(replace.indexOf("local old_code")).isLessThan(replace.indexOf("redis.call('DEL'"));
        assertThat(replace).doesNotContain("raw_code");

        String consume = lua("lua/core_agent_authorization_code_consume.lua");
        assertThat(consume).contains("#KEYS ~= 2", "#ARGV ~= 2", "HGET", "user_id", "DEL", "return 0", "return 1");
        assertThat(consume.indexOf("if #KEYS ~= 2")).isLessThan(consume.indexOf("redis.call('GET'"));
        assertThat(consume.indexOf("redis.call('GET'"))
                .isLessThan(consume.indexOf("redis.call('DEL'"));

        String invalidate = lua("lua/core_agent_authorization_code_invalidate.lua");
        assertThat(invalidate).contains("#KEYS ~= 1", "#ARGV ~= 2", "oauth2:auth_code:", "GET", "DEL", "return 1");
        assertThat(invalidate.indexOf("if #KEYS ~= 1")).isLessThan(invalidate.indexOf("redis.call('GET'"));
        assertThat(invalidate).contains("if not current_code then return 1 end");
    }

    private static RedisCoreAgentAuthorizationCodeStore store(StringRedisTemplate redis, DefaultRedisScript<Long> replace,
                                                               DefaultRedisScript<Long> consume,
                                                               DefaultRedisScript<Long> invalidate, Instant now) {
        return new RedisCoreAgentAuthorizationCodeStore(redis, new CoreAgentAuthorizationCodeStateCodec(),
                Clock.fixed(now, ZoneOffset.UTC), replace, consume, invalidate);
    }

    private static Object[] replaceArguments(CoreAgentAuthorizationCodeState state, String ttlMillis) {
        Map<String, String> values = new CoreAgentAuthorizationCodeStateCodec().encode(state);
        List<Object> arguments = new ArrayList<>();
        arguments.add(ttlMillis);
        arguments.add(state.rawCode());
        arguments.add(Long.toString(state.accountSnapshot().userId()));
        for (String field : new CoreAgentAuthorizationCodeStateCodec().fieldNames()) {
            arguments.add(field);
            arguments.add(values.get(field));
        }
        return arguments.toArray();
    }

    private static CoreAgentAuthorizationCodeState state(String rawCode, Instant issuedAt) {
        return new CoreAgentAuthorizationCodeState(rawCode, "core_agent", "http://127.0.0.1:9090/oauth/callback",
                List.of("note:read", "sys:read"), code((byte) 9), "S256", "127.0.0.1", "opaque-state", issuedAt,
                issuedAt.plus(Duration.ofMinutes(10)), new CoreAgentAuthorizationAccountSnapshot(USER_ID, "alice", 2L,
                "$2a$10$" + "a".repeat(53), null, "agent_client", 0));
    }

    private static String codeKey(String rawCode) {
        return "oauth2:auth_code:{" + rawCode + '}';
    }

    private static String userKey() {
        return "user:auth_code:{7}";
    }

    private static String code(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String lua(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
