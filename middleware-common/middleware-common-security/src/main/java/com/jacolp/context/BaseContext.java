package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthenticationContext;

/**
 * 旧业务代码使用的认证上下文兼容入口。
 */
public final class BaseContext {

    private BaseContext() {
    }

    public static void setCurrentId(Long id) {
        AuthenticationContext.setCurrentId(id);
    }

    public static Long getCurrentId() {
        return AuthenticationContext.getCurrentId();
    }

    public static Long getCurrentIdWithoutValid() {
        return AuthenticationContext.getCurrentIdWithoutValidation();
    }

    public static void remove() {
        AuthenticationContext.clear();
    }
}
