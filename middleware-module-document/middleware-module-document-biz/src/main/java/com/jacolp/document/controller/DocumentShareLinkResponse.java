package com.jacolp.document.controller;

import com.jacolp.document.enums.DocumentPermission;
import java.time.LocalDateTime;
import java.util.Objects;

/** 对外返回的文档分享短链信息；原始令牌只会出现在创建响应的 shareUrl 中。 */
public record DocumentShareLinkResponse(
        long shareLinkId,
        long documentId,
        DocumentPermission permission,
        String shareUrl,
        LocalDateTime expiresAt,
        int maxUses,
        int usedCount,
        boolean enabled,
        LocalDateTime revokedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public DocumentShareLinkResponse {
        if (shareLinkId <= 0 || documentId <= 0) {
            throw new IllegalArgumentException("shareLinkId and documentId must be positive");
        }
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
