package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员审核图片 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long auditId;  // 审核记录ID，必填

    private Boolean approved;  // 是否批准，必填

    private String rejectReason;  // 拒绝原因（仅当 approved=false 时需填）
}
