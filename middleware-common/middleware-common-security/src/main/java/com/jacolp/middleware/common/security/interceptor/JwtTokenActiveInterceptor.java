package com.jacolp.middleware.common.security.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.json.JacksonObjectMapper;
import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import com.jacolp.middleware.common.security.token.SecurityTokenConstants;
import com.jacolp.result.Result;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenActiveInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();
    @Autowired private JwtProperties jwtProperties;

    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        SecurityContextBridge.clear();

        // 1、从请求头中获取令牌
        String[] split = request.getRequestURI().split("/");
        String token = split[split.length - 1];

        // 2、校验令牌
        try {
            if (token == null || token.isBlank()) {
                SecurityContextBridge.clear();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                setResult(response, Result.error("未提供认证令牌"));
                return false;
            }
            log.debug("JWT verification: {}", token);
            Claims claims = JwtTokenSupport.parseJWT(jwtProperties.getActiveSecretKey(), token);
            Long userId = Long.valueOf(claims.get(SecurityTokenConstants.USER_ID_CLAIM).toString());
            log.debug("Current user that need to active ID: {}", userId);
            AuthenticationContext.setCurrentId(userId);

            // 获取激活信号码
            boolean activeCode = Boolean.parseBoolean(claims.get(SecurityTokenConstants.ACTIVE_SIGN_KEY).toString());
            if (!activeCode) {
                SecurityContextBridge.clear();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                setResult(response, Result.error("并激活令牌，无法激活账号"));
                return false;
            }

            SecurityContextBridge.authenticate(userId, SecurityIdentity.ACTIVATION);

            // 3、通过，放行
            return true;
        } catch (Exception ex) {
            SecurityContextBridge.clear();
            // 4、不通过，响应401状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            log.error("JWT verification failed: {}", ex.getMessage());
            setResult(response, Result.error("认证令牌无效或已过期"));
            return false;
        }
    }


    /**
     * 设置响应结果
     *
     * @param response 响应对象
     * @param result   返回结果
     */
    private void setResult(HttpServletResponse response, Result<?> result) {
        try {
            response.setContentType("application/json;charset=UTF-8");
            String json = OBJECT_MAPPER.writeValueAsString(result);
            response.getWriter().write(json);
        } catch (Exception e) {
            log.error("Failed to write response: {}", e.getMessage());
        }
    }

    /**
     * 清理线程数据
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        log.debug("Request completed for admin interceptor");
        AuthenticationContext.clear();
        SecurityContextBridge.clear();
    }
}
