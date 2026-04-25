package com.jacolp.pojo.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端更新笔记内容 DTO
 */
@Data
@Schema(description = "用户端更新笔记内容请求")
public class UserNoteUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @Schema(description = "笔记ID", required = true)
    private Long id;

    /**
     * 主题ID
     */
    @Schema(description = "主题ID")
    private Long topicId;

    /**
     * 笔记标题
     */
    @Schema(description = "笔记标题")
    private String title;

    /**
     * 笔记描述
     */
    @Schema(description = "笔记描述")
    private String description;
}