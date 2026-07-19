package com.jacolp.context;

/**
 * 权限上下文 —— 区分当前请求是管理端还是用户端操作。
 *
 * <p>由 JWT 拦截器在请求进入时设置，在 {@code afterCompletion} 中清理。
 * 子 Service 可通过 {@link #isAdmin()} 判断是否跳过所有权校验等额外逻辑，
 * 从而让同一套方法同时服务于管理端和用户端。</p>
 *
 * <pre>
 * JwtTokenAdminInterceptor → PermissionContext.setAdmin(true)
 * JwtTokenUserInterceptor  → PermissionContext.setAdmin(false)
 * </pre>
 */
public class PermissionContext {

    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();

    /**
     * 设置当前请求的管理端标识。
     * @param isAdmin true 表示管理端，false 表示用户端
     */
    public static void setAdmin(boolean isAdmin) {
        IS_ADMIN.set(isAdmin);
    }

    /**
     * 判断当前请求是否为管理端操作。
     * @return true 表示管理端，false 表示用户端（或未设置）
     */
    public static boolean isAdmin() {
        return Boolean.TRUE.equals(IS_ADMIN.get());
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏。
     */
    public static void remove() {
        IS_ADMIN.remove();
    }
}
