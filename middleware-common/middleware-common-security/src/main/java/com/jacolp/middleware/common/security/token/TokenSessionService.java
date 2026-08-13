package com.jacolp.middleware.common.security.token;

/** Stable token and Redis-session boundary for account workflows. */
public interface TokenSessionService {
    String issueUserLoginToken(Long userId);
    String issueAdminLoginToken(Long adminId);
    void revokeUserLoginToken(Long userId);
    void revokeAdminLoginToken(Long adminId);
}
