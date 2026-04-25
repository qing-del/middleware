package com.jacolp.pojo.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端获取图片详情 DTO
 */
@Data
@Schema(description = "用户端获取图片详情请求")
public class UserImageDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    @Schema(description = "图片ID", required = true)
    private Long id;
}