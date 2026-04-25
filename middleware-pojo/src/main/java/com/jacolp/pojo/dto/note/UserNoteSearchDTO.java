package com.jacolp.pojo.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端笔记搜索 DTO
 */
@Data
@Schema(description = "用户端笔记搜索请求")
public class UserNoteSearchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词
     */
    @Schema(description = "搜索关键词", required = true)
    private String keyword;

    /**
     * 主题ID
     */
    @Schema(description = "主题ID")
    private Long topicId;

    /**
     * 标签ID
     */
    @Schema(description = "标签ID")
    private Long tagId;

    /**
     * 页码
     */
    @Schema(description = "页码")
    private Integer pageNum;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小")
    private Integer pageSize;
}