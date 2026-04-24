package com.jacolp.pojo.dto.tag;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端标签条件查询 DTO。
 * 查询逻辑：当前用户自己的标签 + 别人已通过审核的标签。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTagQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String keyword;

    private Integer pageNum;

    private Integer pageSize;
}
