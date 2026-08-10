package com.jacolp.middleware.common.security.oauth2.token;

import java.util.Optional;

public interface OAuth2TokenStateStore {
    Optional<RefreshTokenState> findRefreshByFingerprint(String fingerprint);
    Optional<OAuth2SessionState> findSession(String clientId, long userId);
    void deleteRefresh(String fingerprint);
    void deleteSession(String clientId, long userId);
}
