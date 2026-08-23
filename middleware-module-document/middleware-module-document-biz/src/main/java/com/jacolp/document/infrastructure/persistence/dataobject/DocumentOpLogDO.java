package com.jacolp.document.infrastructure.persistence.dataobject;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Durable Yjs update accepted from the Redis pending stream. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentOpLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private String redisOpId;
    private String clientUpdateId;
    private byte[] updateData;
    private Long operatorId;
    private String operatorType;
    private LocalDateTime createTime;
}
