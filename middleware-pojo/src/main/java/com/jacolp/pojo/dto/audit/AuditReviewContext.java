package com.jacolp.pojo.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditReviewContext {
    private List<Long> ids;
    private Short status;
    private Long reviewerUserId;
    private String rejectReason;
}
