package com.jacolp.pojo.vo.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签审核列表展示对象。
 * applyType 保留为兼容字段，固定为 2。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAuditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long applicantUserId;

    private String applicantUsername;

    private Short applyType;

    private Long targetId;

    private String targetName;

    private String applyReason;

    private Short status;

    private Long reviewerUserId;

    private String reviewerUsername;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime reviewTime;

    private LocalDateTime updateTime;
}
