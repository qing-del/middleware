package com.jacolp.middleware.common.security.context;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Mirrors successfully authenticated legacy MVC requests into Spring Security's context. */
public final class SecurityContextBridge {

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
}
