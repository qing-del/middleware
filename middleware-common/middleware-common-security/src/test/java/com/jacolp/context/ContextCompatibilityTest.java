package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import com.jacolp.middleware.common.security.context.SecurityContextBridge;
import com.jacolp.middleware.common.security.context.SecurityIdentity;
import com.jacolp.exception.AuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextCompatibilityTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
        SecurityContextBridge.clear();
    }

    @Test
    void newSecurityContextIsVisibleThroughLegacyFacade() {
        SecurityContextBridge.authenticate(101L, SecurityIdentity.ADMIN);

        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void legacyFacadeWritesToNewSecurityContext() {
        BaseContext.setCurrentId(202L);
        PermissionContext.setAdmin(false);

        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(202L);
        assertThat(AuthorizationContext.isAdmin()).isFalse();
    }

    @Test
    void holderTakesPrecedenceAndMapsAllAuthorities() {
        AuthenticationContext.setCurrentId(9L);
        AuthorizationContext.setAdmin(true);
        SecurityContextBridge.authenticate(101L, SecurityIdentity.USER);
        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isFalse();
        SecurityContextBridge.authenticate(102L, SecurityIdentity.ACTIVATION);
        assertThat(PermissionContext.isAdmin()).isFalse();
        SecurityContextBridge.authenticate(103L, SecurityIdentity.ADMIN);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void absentHolderFallsBackAndAbsentIdentityRetainsLegacyException() {
        BaseContext.setCurrentId(88L);
        PermissionContext.setAdmin(true);
        assertThat(BaseContext.getCurrentIdWithoutValid()).isEqualTo(88L);
        assertThat(PermissionContext.isAdmin()).isTrue();
        BaseContext.remove();
        org.assertj.core.api.Assertions.assertThatThrownBy(BaseContext::getCurrentId)
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("当前登录信息已失效");
    }

    @Test
    void holderAuthorityOverridesPrincipalIdentity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new com.jacolp.middleware.common.security.context.SecurityPrincipal(1L, SecurityIdentity.ADMIN), null,
                List.of(new SimpleGrantedAuthority(SecurityIdentity.USER.authority()))));
        assertThat(PermissionContext.isAdmin()).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new com.jacolp.middleware.common.security.context.SecurityPrincipal(2L, SecurityIdentity.USER), null,
                List.of(new SimpleGrantedAuthority(SecurityIdentity.ADMIN.authority()))));
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void completeJwtHolderIdentityIsVisibleThroughLegacyFacades() {
        authenticateJwt(jwt("101", "ADMIN"));
        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isTrue();
        authenticateJwt(jwt("102", "USER"));
        assertThat(BaseContext.getCurrentId()).isEqualTo(102L);
        assertThat(PermissionContext.isAdmin()).isFalse();
    }

    @Test
    void malformedJwtPreventsThreadLocalFallback() {
        BaseContext.setCurrentId(88L);
        PermissionContext.setAdmin(true);
        authenticateJwt(jwt("not-a-number", "ADMIN"));

        assertThatThrownBy(BaseContext::getCurrentId).isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(PermissionContext::isAdmin).isInstanceOf(AuthenticationException.class);
    }

    private static void authenticateJwt(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                jwt, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("opaque.jwt")
                .header("alg", "RS256")
                .subject(subject)
                .claim("username", "alice")
                .claim("client_id", "user_client")
                .claim("grant_type", "password")
                .claim("roles", List.of(role))
                .claim("scope", List.of("*:read"))
                .issuedAt(Instant.parse("2026-08-10T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-10T01:00:00Z"))
                .build();
    }
}
