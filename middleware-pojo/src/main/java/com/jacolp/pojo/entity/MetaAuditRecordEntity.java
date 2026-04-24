package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元数据审核记录表 biz_meta_audit_record 对应实体。
 * 用于审核主题和标签的申请记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAuditRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long applicantUserId;

    private Short applyType;  // 申请类型：1-主题, 2-标签

    private Long targetId;  // 关联的主题ID或标签ID

    private String applyReason;

    private Short status;  // 审核状态：0-待审核, 1-已通过, 2-已拒绝

    private Long reviewerUserId;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime reviewTime;

    private LocalDateTime updateTime;
}
