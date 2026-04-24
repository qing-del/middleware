package com.jacolp.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.json.JacksonObjectMapper;
import com.jacolp.properties.JwtProperties;
import com.jacolp.result.Result;
import com.jacolp.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for JWT token verification
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired private JwtProperties jwtProperties;
    private static final ObjectMapper OBJECT_MAPPER = new JacksonObjectMapper();

    /**
     * verify JWT token
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
        // determine whether the currently intercepted method is for the Controller or for some other resources
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 2、校验令牌
        try {
            if (token == null || token.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            log.info("JWT verification: {}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(UserConstant.USER_ID_CLAIM).toString());
            log.info("Current user ID: {}", userId);
            // 3、将用户 ID 存入线程上下文
            BaseContext.setCurrentId(userId);
            // 4、通过，放行
            return true;
        } catch (Exception ex) {
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
     * @param request
     * @param response
     * @param handler
     * @param ex
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        log.debug("Request completed for user interceptor");
        BaseContext.remove();
    }
}
