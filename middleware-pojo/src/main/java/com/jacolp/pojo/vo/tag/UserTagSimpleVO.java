package com.jacolp.pojo.vo.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户端标签列表响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端标签信息")
public class UserTagSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID")
    private Long id;

    /**
     * 标签名称
     */
    @Schema(description = "标签名称")
    private String tagName;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}