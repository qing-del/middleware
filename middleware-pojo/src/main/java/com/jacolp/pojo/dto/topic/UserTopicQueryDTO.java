package com.jacolp.pojo.dto.topic;

import java.io.Serializable;

import com.jacolp.pojo.provider.PageParamProvider;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端主题条件查询 DTO。
 * 查询逻辑：当前用户自己的主题 + 别人已通过审核的主题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTopicQueryDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private String keyword;

    @Positive(message = "页码必须为正数")
    private Integer pageNum;

    @Positive(message = "每页数量必须为正数")
    private Integer pageSize;
}
