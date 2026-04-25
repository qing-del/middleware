package com.jacolp.pojo.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端删除图片 DTO
 */
@Data
@Schema(description = "用户端删除图片请求")
public class UserImageDeleteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    @Schema(description = "图片ID", required = true)
    private Long id;
}