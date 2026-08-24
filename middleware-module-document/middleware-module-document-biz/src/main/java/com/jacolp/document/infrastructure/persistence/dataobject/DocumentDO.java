package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** v0.3 {@code biz_document} 表的一行持久化模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** v0.3 中的个人空间标识，始终保存文档所有者的用户 ID。 */
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
