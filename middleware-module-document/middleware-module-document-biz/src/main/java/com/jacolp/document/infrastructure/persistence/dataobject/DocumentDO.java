package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Persistence model for the new v0.3 {@code biz_document} table. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** Personal scope in v0.3; this is always the owner user ID. */
    private Long teamId;
    private String title;
    private String contentObjectKey;
    private Long persistedLogId;
    private LocalDateTime lastModifyTime;
    private Long lastModifyUserId;
    private Boolean deleted;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
