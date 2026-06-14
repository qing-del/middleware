package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签审核记录表 biz_tag_audit_record 对应实体。
 * applyType 保留为兼容字段，固定为 2。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAuditRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long applicantUserId;

    private Short applyType;  // 兼容字段：2-标签

    private Long targetId;  // 关联的标签ID

    private String applyReason;

    private Short status;  // 审核状态：0-待审核, 1-审核中, 2-已通过, 3-已拒绝, 4-已删除

    private Long reviewerUserId;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime reviewTime;

    private LocalDateTime updateTime;
}
