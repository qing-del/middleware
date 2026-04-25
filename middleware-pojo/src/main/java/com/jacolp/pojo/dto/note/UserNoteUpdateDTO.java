package com.jacolp.pojo.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端更新笔记内容 DTO
 */
@Data
@Schema(description = "更新笔记内容请求")
public class UserNoteUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "笔记ID", example = "1", required = true)
    private Long id;

    @Schema(description = "主题ID", example = "1")
    private Long topicId;

    @Schema(description = "笔记标题", example = "更新后的标题")
    private String title;

    @Schema(description = "笔记描述", example = "更新后的描述")
    private String description;
}