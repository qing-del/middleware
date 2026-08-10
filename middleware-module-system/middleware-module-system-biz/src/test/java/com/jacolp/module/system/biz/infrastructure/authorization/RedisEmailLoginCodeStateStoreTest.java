package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.application.authorization.EmailLoginCodeStateCodec;
import com.jacolp.module.system.biz.application.authorization.model.EmailLoginCodeState;
import com.jacolp.module.system.biz.application.port.out.EmailLoginCodeStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailLoginCodeStateStoreTest {
    @Test
    void portDoesNotExposeInfrastructureTypesAndProductionConstructorIsAutowired() throws Exception {
        for (Method method : EmailLoginCodeStateStore.class.getDeclaredMethods()) {
            assertThat(method.getGenericReturnType().getTypeName()).doesNotContain(".infrastructure.");
        }
        assertThat(RedisEmailLoginCodeStateStore.class.getConstructors()[0].isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsExactKeyAndDecodesMatchingState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        EmailLoginCodeState state = state("user", 7L);
        when(hash.entries("user:email_code:user:7")).thenReturn(new LinkedHashMap<>(new EmailLoginCodeStateCodec().encode(state)));
        assertThat(new RedisEmailLoginCodeStateStore(redis).find("user", 7L)).contains(state);
        verify(hash).entries("user:email_code:user:7");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullAndEmptyHashesAreMissing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class); HashOperations<String,Object,Object> hash=mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash); when(hash.entries("user:email_code:user:7")).thenReturn(null, Map.of());
        RedisEmailLoginCodeStateStore store=new RedisEmailLoginCodeStateStore(redis);
        assertThat(store.find("user",7L)).isEmpty(); assertThat(store.find("user",7L)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsNonStringBadOrMismatchedHashes() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class); HashOperations<String,Object,Object> hash=mock(HashOperations.class); when(redis.opsForHash()).thenReturn(hash);
        when(hash.entries("user:email_code:user:7")).thenReturn(Map.of(1,"x")); RedisEmailLoginCodeStateStore store=new RedisEmailLoginCodeStateStore(redis);
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("user",7L));
    }

    @Test void deleteUsesExactKeyAndInvalidParametersFailFast() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class); RedisEmailLoginCodeStateStore store=new RedisEmailLoginCodeStateStore(redis);
        store.delete("admin",9L); verify(redis).delete("user:email_code:admin:9");
        assertThatIllegalArgumentException().isThrownBy(() -> store.find("core_agent",1L));
        assertThatIllegalArgumentException().isThrownBy(() -> store.delete("user",0L));
    }

    @Test @SuppressWarnings("unchecked") void redisExceptionsPropagate() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class); HashOperations<String,Object,Object> hash=mock(HashOperations.class); when(redis.opsForHash()).thenReturn(hash);
        IllegalStateException failure=new IllegalStateException("redis unavailable"); when(hash.entries("user:email_code:user:7")).thenThrow(failure);
        assertThatThrownBy(() -> new RedisEmailLoginCodeStateStore(redis).find("user",7L)).isSameAs(failure);
    }
    private static EmailLoginCodeState state(String client, Long id) { return new EmailLoginCodeState(client,id,"A".repeat(43),"$2a$10$"+"a".repeat(53),0,Instant.ofEpochMilli(1),Instant.ofEpochMilli(2)); }
}
