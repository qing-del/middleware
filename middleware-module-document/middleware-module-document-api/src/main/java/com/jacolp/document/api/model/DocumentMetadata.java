package com.jacolp.document.api.model;

import java.util.Objects;

/** 可跨模块传递的最小文档元数据；不包含 CRDT 正文和对象存储指针。 */
public record DocumentMetadata(
        long documentId,
        long teamId,
        String title,
        long lastModifyTime,
        Long lastModifyUserId,
        boolean deleted
) {
    public DocumentMetadata {
        if (documentId <= 0) throw new IllegalArgumentException("documentId must be positive");
        if (teamId <= 0) throw new IllegalArgumentException("teamId must be positive");
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("title must not be blank");
        if (lastModifyTime < 0) throw new IllegalArgumentException("lastModifyTime must not be negative");
    }
}
