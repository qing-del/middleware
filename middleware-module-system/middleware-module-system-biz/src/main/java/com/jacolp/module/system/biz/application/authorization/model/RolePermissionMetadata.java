package com.jacolp.module.system.biz.application.authorization.model;

import java.time.LocalDateTime;

/**
 * Application-facing direct role-to-permission relation metadata.
 */
public record RolePermissionMetadata(
        Long roleId,
        Long permId,
        LocalDateTime grantTime) {
}
