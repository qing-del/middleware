package com.jacolp.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记审核列表展示对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteAuditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long applicantUserId;

    private String applicantUsername;

    private Long noteId;

    private String noteTitle;

    private String applyReason;

    private Short status;

    private Long reviewerUserId;

    private String reviewerUsername;

    private String rejectReason;

    private LocalDateTime createTime;

    private LocalDateTime reviewTime;

    private LocalDateTime updateTime;
}
