package com.jacolp.document.infrastructure.redis;

/** 单个文档 Room 的 Redis 运行时元数据，始终不保存文档正文。 */
public record DocumentRoomMeta(
        long documentId,
        long teamId,
        boolean closeRequested,
        String closeToken,
        long lastModifyTime,
        Long lastModifyUserId) {

    public DocumentRoomMeta {
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        if (lastModifyTime < 0) {
            throw new IllegalArgumentException("lastModifyTime must not be negative");
        }
        if (closeToken != null && closeToken.isBlank()) {
            throw new IllegalArgumentException("closeToken must be nonblank when present");
        }
        if (closeRequested && closeToken == null) {
            throw new IllegalArgumentException("closeToken is required when close is requested");
        }
    }
}
