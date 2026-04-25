package com.jacolp.pojo.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端绑定标签 DTO
 */
@Data
@Schema(description = "用户端绑定标签请求")
public class UserTagAssignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID", required = true)
    private Long tagId;

    /**
     * 目标资源ID（笔记ID或主题ID）
     */
    @Schema(description = "目标资源ID（笔记ID或主题ID）", required = true)
    private Long targetId;

    /**
     * 目标资源类型
     */
    @Schema(description = "目标资源类型", required = true)
    private String targetType;
}