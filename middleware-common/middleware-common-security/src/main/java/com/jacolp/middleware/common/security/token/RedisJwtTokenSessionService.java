package com.jacolp.middleware.common.security.token;

import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.middleware.common.security.jwt.JwtTokenSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RedisJwtTokenSessionService implements TokenSessionService {
    private final StringRedisTemplate redis;
    private final JwtProperties properties;

    public RedisJwtTokenSessionService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issueUserLoginToken(Long userId) { return issueLogin(userId, false); }
    public String issueAdminLoginToken(Long adminId) { return issueLogin(adminId, true); }
    public void revokeUserLoginToken(Long userId) { redis.delete(SecurityTokenKeyGenerator.getUserLoginKey(userId)); }
    public void revokeAdminLoginToken(Long adminId) { redis.delete(SecurityTokenKeyGenerator.getAdminLoginKey(adminId)); }
    private String issueLogin(Long id, boolean admin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(admin ? SecurityTokenConstants.ADMIN_ID_CLAIM : SecurityTokenConstants.USER_ID_CLAIM, id);
        String token = JwtTokenSupport.createJWT(admin ? properties.getAdminSecretKey() : properties.getUserSecretKey(),
                admin ? properties.getAdminTtl() : properties.getUserTtl(), claims);
        redis.opsForValue().set(admin ? SecurityTokenKeyGenerator.getAdminLoginKey(id)
                : SecurityTokenKeyGenerator.getUserLoginKey(id), token);
        return token;
    }
}
