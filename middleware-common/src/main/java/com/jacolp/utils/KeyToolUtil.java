package com.jacolp.utils;

import com.jacolp.constant.UserConstant;
import org.jspecify.annotations.NonNull;

public class KeyToolUtil {
    /**
     * 获取用户登录令牌对应的 key
     * @param userId
     * @return
     */
    public static @NonNull String getAdminLoginKey(Long userId) {
        return UserConstant.ADMIN_ID_CLAIM + ":" + userId;
    }

    /**
     * 获取用户登录标识 key
     * @param userId 用户ID
     * @return 登录标识
     */
    public static @NonNull String getUserLoginKey(Long userId) {
        return UserConstant.USER_ID_CLAIM + ":" + userId;
    }

    /**
     * 获取激活码对应的 Redis key（6位数字 → userId）
     * @param code 6位激活码
     * @return Redis key
     */
    public static @NonNull String getActiveCodeKey(String code) {
        return UserConstant.ACTIVE_CODE_PREFIX + code;
    }
}
