package com.jacolp.pojo.dto.audit;

import com.jacolp.pojo.provider.PageParamProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 元数据审核列表查询参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaAuditListDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private Short applyType;

    private Short status;

    private Long applicantUserId;

    private Integer pageNum;

    private Integer pageSize;
}
