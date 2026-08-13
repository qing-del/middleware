package com.jacolp.middleware.common.security.token;

import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisJwtTokenSessionServiceTest {
    private static final String USER_SECRET = "user-secret-key-for-token-session-tests";
    private static final String ADMIN_SECRET = "admin-secret-key-for-token-session-tests";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisJwtTokenSessionService service;
    private JwtProperties properties;

    @BeforeEach @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new JwtProperties();
        properties.setUserSecretKey(USER_SECRET); properties.setAdminSecretKey(ADMIN_SECRET);
        properties.setUserTtl(60001L); properties.setAdminTtl(60002L);
        service = new RedisJwtTokenSessionService(redis, properties);
    }

    @Test void issuesUserAndAdminTokensWithoutRedisTtl() {
        long userStarted = System.currentTimeMillis();
        String user = service.issueUserLoginToken(11L);
        long userFinished = System.currentTimeMillis();
        long adminStarted = System.currentTimeMillis();
        String admin = service.issueAdminLoginToken(12L);
        long adminFinished = System.currentTimeMillis();
        Claims uc = JwtTokenSupport.parseJWT(USER_SECRET, user); Claims ac = JwtTokenSupport.parseJWT(ADMIN_SECRET, admin);
        assertThat(uc.get(SecurityTokenConstants.USER_ID_CLAIM).toString()).isEqualTo("11");
        assertThat(ac.get(SecurityTokenConstants.ADMIN_ID_CLAIM).toString()).isEqualTo("12");
        assertThat(uc.getExpiration().getTime()).isBetween(userStarted + 59001L, userFinished + 60001L);
        assertThat(ac.getExpiration().getTime()).isBetween(adminStarted + 59002L, adminFinished + 60002L);
        verify(values).set(SecurityTokenKeyGenerator.getUserLoginKey(11L), user);
        verify(values).set(SecurityTokenKeyGenerator.getAdminLoginKey(12L), admin);
        verify(values, never()).set(any(), any(), any(java.time.Duration.class));
    }

    @Test void revokesUserAndAdministratorSessions() {
        service.revokeUserLoginToken(11L); service.revokeAdminLoginToken(12L);
        verify(redis).delete(SecurityTokenKeyGenerator.getUserLoginKey(11L)); verify(redis).delete(SecurityTokenKeyGenerator.getAdminLoginKey(12L));
    }
}
