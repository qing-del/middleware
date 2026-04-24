package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记审核记录表 biz_note_audit_record 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteAuditRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long applicantUserId;

    private Long noteId;

    private String applyReason;

    private Short status;  // 审核状态：0-待审核, 1-已通过, 2-已拒绝

    private Long reviewerUserId;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime reviewTime;

    private LocalDateTime updateTime;
}
