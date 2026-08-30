package com.jacolp.document.api.model;

import java.util.Objects;

/** 可跨模块传递的最小文档元数据；不包含 CRDT 正文和对象存储指针。 */
public record DocumentMetadata(
        /** 文档数据库主键。<p>example: {@code 42}</p> */
        long documentId,
        /** 文档所有者用户 ID。<p>example: {@code 10001}</p> */
        long ownerUserId,
        /** 文档标题，不包含 CRDT 正文。<p>example: {@code 项目设计文档}</p> */
        String title,
        /** 最近一次接受更新的时间戳，单位为 Unix 毫秒。<p>example: {@code 1756080000000}</p> */
        long lastModifyTime,
        /** 最近修改文档的用户 ID；尚无修改者时为空。<p>example: {@code 10001}</p> */
        Long lastModifyUserId,
        /** 文档是否已逻辑删除。<p>example: {@code false}</p> */
        boolean deleted
) {
    /** 校验元数据的范围和标题约束，避免无效状态跨模块传播。 */
    public DocumentMetadata {
        if (documentId <= 0) throw new IllegalArgumentException("documentId must be positive");
        if (ownerUserId <= 0) throw new IllegalArgumentException("ownerUserId must be positive");
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("title must not be blank");
        if (lastModifyTime < 0) throw new IllegalArgumentException("lastModifyTime must not be negative");
    }
}
