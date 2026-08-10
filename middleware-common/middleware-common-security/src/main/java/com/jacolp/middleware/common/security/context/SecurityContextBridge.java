package com.jacolp.middleware.common.security.context;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Mirrors successfully authenticated legacy MVC requests into Spring Security's context. */
public final class SecurityContextBridge {
    private static final CurrentPrincipalAccessor CURRENT_PRINCIPAL_ACCESSOR = new SecurityContextCurrentPrincipalAccessor();

    private SecurityContextBridge() {
    }

    public static void authenticate(Long id, SecurityIdentity identity) {
        SecurityPrincipal principal = new SecurityPrincipal(id, identity);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(identity.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    public static Long currentIdOrNull() {
        return CURRENT_PRINCIPAL_ACCESSOR.currentPrincipal().map(CurrentPrincipal::userId).orElse(null);
    }

    /** Returns null only when no supported holder identity exists, so callers can use their legacy fallback. */
    public static Boolean isAdminOrNull() {
        return CURRENT_PRINCIPAL_ACCESSOR.currentPrincipal().map(CurrentPrincipal::isAdministrative).orElse(null);
    }
}
