package com.jacolp.pojo.vo.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户端笔记详情响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端笔记详情信息")
public class UserNoteDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @Schema(description = "笔记ID")
    private Long id;

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

    /**
     * Markdown原文
     */
    @Schema(description = "Markdown原文")
    private String markdownContent;

    /**
     * 渲染后的HTML
     */
    @Schema(description = "渲染后的HTML")
    private String htmlContent;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 是否发布
     */
    @Schema(description = "是否发布")
    private Short isPublished;
}