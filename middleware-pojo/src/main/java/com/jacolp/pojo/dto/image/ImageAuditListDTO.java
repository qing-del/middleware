package com.jacolp.pojo.dto.image;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片审核列表查询参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Short status;

    private Long applicantUserId;

    private Integer pageNum;

    private Integer pageSize;
}
