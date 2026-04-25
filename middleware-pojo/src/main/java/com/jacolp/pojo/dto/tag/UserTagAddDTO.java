package com.jacolp.pojo.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端创建标签 DTO
 */
@Data
@Schema(description = "用户端创建标签请求")
public class UserTagAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签名称
     */
    @Schema(description = "标签名称", required = true)
    private String tagName;
}