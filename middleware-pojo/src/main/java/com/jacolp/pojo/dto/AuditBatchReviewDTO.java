package com.jacolp.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用批量审核入参。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditBatchReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Long> ids;

    private Short status;

    private String rejectReason;
}
