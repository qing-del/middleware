package com.jacolp.middleware.common.security.interceptor;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import com.jacolp.middleware.common.security.token.SecurityTokenConstants;
import com.jacolp.middleware.common.security.token.SecurityTokenKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtTokenInterceptorCharacterizationTest {

    private static final String USER_SECRET = "user-secret-key-for-characterization";
    private static final String ADMIN_SECRET = "admin-secret-key-for-characterization";
    private static final String ACTIVE_SECRET = "active-secret-key-for-characterization";
    private static final long USER_ID = 101L;
    private static final long ADMIN_ID = 7L;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private QpsCounter qpsCounter;
    private JwtProperties jwtProperties;
    private HandlerMethod handlerMethod;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws NoSuchMethodException {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        qpsCounter = mock(QpsCounter.class);
        when(redis.opsForValue()).thenReturn(valueOperations);

        jwtProperties = new JwtProperties();
        jwtProperties.setUserSecretKey(USER_SECRET);
        jwtProperties.setAdminSecretKey(ADMIN_SECRET);
        jwtProperties.setActiveSecretKey(ACTIVE_SECRET);

        Method method = HandlerTarget.class.getDeclaredMethod("handle");
        handlerMethod = new HandlerMethod(new HandlerTarget(), method);
    }

    @AfterEach
    void clearContexts() {
        AuthenticationContext.clear();
        AuthorizationContext.clear();
    }

    @Test
    void userInterceptorRejectsMissingTokenWithoutWritingResponseBody() {
        JwtTokenUserInterceptor interceptor = userInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).isEmpty();
        verify(qpsCounter).increment();
    }

    @Test
    void userInterceptorRejectsInvalidTokenWithCompatibleJsonResponse() {
        JwtTokenUserInterceptor interceptor = userInterceptor();
        MockHttpServletRequest request = bearerRequest("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isFalse();
        assertUnauthorizedJson(response, "认证令牌无效或已过期");
    }

    @Test
    void userInterceptorRejectsTokenMissingFromRedis() {
        JwtTokenUserInterceptor interceptor = userInterceptor();
        String token = userToken(USER_ID);
        when(valueOperations.get(SecurityTokenKeyGenerator.getUserLoginKey(USER_ID))).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(bearerRequest(token), response, handlerMethod)).isFalse();

        assertUnauthorizedJson(response, "认证令牌无效或已过期");
    }

    @Test
    void userInterceptorSetsUserContextAndCleansItAfterCompletion() {
        JwtTokenUserInterceptor interceptor = userInterceptor();
        String token = userToken(USER_ID);
        when(valueOperations.get(SecurityTokenKeyGenerator.getUserLoginKey(USER_ID))).thenReturn(token);

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(USER_ID);
        assertThat(AuthorizationContext.isAdmin()).isFalse();

        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(AuthenticationContext.getCurrentIdWithoutValidation()).isNull();
        assertThat(AuthorizationContext.isAdmin()).isFalse();
    }

    @Test
    void adminInterceptorSetsAdminContextAndCleansItAfterCompletion() {
        JwtTokenAdminInterceptor interceptor = adminInterceptor();
        String token = adminToken(ADMIN_ID);
        when(valueOperations.get(SecurityTokenKeyGenerator.getAdminLoginKey(ADMIN_ID))).thenReturn(token);

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(ADMIN_ID);
        assertThat(AuthorizationContext.isAdmin()).isTrue();

        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(AuthenticationContext.getCurrentIdWithoutValidation()).isNull();
        assertThat(AuthorizationContext.isAdmin()).isFalse();
    }

    @Test
    void adminInterceptorRejectsRedisTokenMismatch() {
        JwtTokenAdminInterceptor interceptor = adminInterceptor();
        String token = adminToken(ADMIN_ID);
        when(valueOperations.get(SecurityTokenKeyGenerator.getAdminLoginKey(ADMIN_ID))).thenReturn("other-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(bearerRequest(token), response, handlerMethod)).isFalse();

        assertUnauthorizedJson(response, "认证令牌已过期");
    }

    @Test
    void adminInterceptorBypassesNonHandlerMethodWithoutCountingQps() {
        JwtTokenAdminInterceptor interceptor = adminInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
        verify(qpsCounter, never()).increment();
    }

    @Test
    void activeInterceptorAcceptsSignedActivationTokenAndCleansContext() throws Exception {
        JwtTokenActiveInterceptor interceptor = activeInterceptor();
        String token = activeToken(USER_ID, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/user/active/" + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(USER_ID);

        interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(AuthenticationContext.getCurrentIdWithoutValidation()).isNull();
    }

    @Test
    void activeInterceptorRejectsTokenWithoutActivationFlag() throws Exception {
        JwtTokenActiveInterceptor interceptor = activeInterceptor();
        String token = activeToken(USER_ID, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/user/active/" + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isFalse();
        assertUnauthorizedJson(response, "并激活令牌，无法激活账号");
    }

    @Test
    void staticResourcesBypassUserInterceptorWithoutCountingQps() {
        JwtTokenUserInterceptor interceptor = userInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
        verify(qpsCounter, never()).increment();
    }

    @Test
    void activeInterceptorRejectsInvalidTokenWithCompatibleJsonResponse() throws Exception {
        JwtTokenActiveInterceptor interceptor = activeInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/user/active/invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod)).isFalse();
        assertUnauthorizedJson(response, "认证令牌无效或已过期");
    }

    @Test
    void activeInterceptorBypassesNonHandlerMethod() throws Exception {
        JwtTokenActiveInterceptor interceptor = activeInterceptor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
    }

    private JwtTokenUserInterceptor userInterceptor() {
        JwtTokenUserInterceptor interceptor = new JwtTokenUserInterceptor();
        ReflectionTestUtils.setField(interceptor, "redis", redis);
        ReflectionTestUtils.setField(interceptor, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(interceptor, "qpsCounter", qpsCounter);
        return interceptor;
    }

    private JwtTokenAdminInterceptor adminInterceptor() {
        JwtTokenAdminInterceptor interceptor = new JwtTokenAdminInterceptor();
        ReflectionTestUtils.setField(interceptor, "redis", redis);
        ReflectionTestUtils.setField(interceptor, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(interceptor, "qpsCounter", qpsCounter);
        return interceptor;
    }

    private JwtTokenActiveInterceptor activeInterceptor() {
        JwtTokenActiveInterceptor interceptor = new JwtTokenActiveInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtProperties", jwtProperties);
        return interceptor;
    }

    private static MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String userToken(long userId) {
        return JwtTokenSupport.createJWT(USER_SECRET, 60_000,
                new HashMap<>(Map.of(SecurityTokenConstants.USER_ID_CLAIM, userId)));
    }

    private static String adminToken(long adminId) {
        return JwtTokenSupport.createJWT(ADMIN_SECRET, 60_000,
                new HashMap<>(Map.of(SecurityTokenConstants.ADMIN_ID_CLAIM, adminId)));
    }

    private static String activeToken(long userId, boolean active) {
        return JwtTokenSupport.createJWT(ACTIVE_SECRET, 60_000, new HashMap<>(Map.of(
                SecurityTokenConstants.USER_ID_CLAIM, userId,
                SecurityTokenConstants.ACTIVE_SIGN_KEY, active)));
    }

    private static void assertUnauthorizedJson(MockHttpServletResponse response, String message) {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("\"code\":0")
                .contains("\"msg\":\"" + message + "\"");
    }

    private static final class HandlerTarget {
        public void handle() {
        }
    }
}
