package com.jacolp.pojo.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端解除标签绑定 DTO
 */
@Data
@Schema(description = "解除标签绑定请求")
public class UserTagRemoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "标签ID", example = "1", required = true)
    private Long tagId;

    @Schema(description = "目标资源ID（笔记ID或主题ID）", example = "1", required = true)
    private Long targetId;

    @Schema(description = "目标资源类型：note-笔记，topic-主题", example = "note", required = true)
    private String targetType;
}