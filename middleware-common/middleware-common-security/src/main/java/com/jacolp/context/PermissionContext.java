package com.jacolp.context;

import com.jacolp.middleware.common.security.context.CurrentPrincipalAccessor;
import com.jacolp.middleware.common.security.context.SecurityContextCurrentPrincipalAccessor;

/**
 * 旧业务代码使用的权限上下文兼容入口。
 */
public final class PermissionContext {

    private static final CurrentPrincipalAccessor CURRENT_PRINCIPAL_ACCESSOR = new SecurityContextCurrentPrincipalAccessor();

    private PermissionContext() {
    }

    public static boolean isAdmin() {
        return CURRENT_PRINCIPAL_ACCESSOR.currentPrincipal().map(principal -> principal.isAdministrative()).orElse(false);
    }
}
