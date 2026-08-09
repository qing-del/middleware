package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.constant.RoleConstant;

/**
 * Fixed account grant metadata for the three supported system roles.
 */
public final class UserGrantTypePolicy {

    public static final String USER_GRANT_TYPES = "password,user_password,agent_client";
    public static final String PRIVILEGED_GRANT_TYPES = "admin_password,agent_client";

    private UserGrantTypePolicy() {
    }

    public static String forRoleId(Long roleId) {
        if (Long.valueOf(RoleConstant.USER).equals(roleId)) {
            return USER_GRANT_TYPES;
        }
        if (Long.valueOf(RoleConstant.ADMIN).equals(roleId)
                || Long.valueOf(RoleConstant.CREATOR).equals(roleId)) {
            return PRIVILEGED_GRANT_TYPES;
        }
        throw new IllegalArgumentException("No account grant metadata is defined for roleId=" + roleId);
    }
}
