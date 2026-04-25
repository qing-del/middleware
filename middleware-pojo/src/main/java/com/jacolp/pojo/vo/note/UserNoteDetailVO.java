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

    @Schema(description = "笔记ID", example = "1")
    private Long id;

    @Schema(description = "笔记标题", example = "我的第一篇笔记")
    private String title;

    @Schema(description = "笔记描述", example = "这是一篇关于Java的笔记")
    private String description;

    @Schema(description = "Markdown原文", example = "# 标题\\n\\n这是正文内容...")
    private String markdownContent;

    @Schema(description = "渲染后的HTML", example = "<h1>标题</h1><p>这是正文内容...</p>")
    private String htmlContent;

    @Schema(description = "标签列表", example = "[\"Java\", \"Spring\"]")
    private List<String> tags;

    @Schema(description = "创建时间", example = "2024-01-15T10:30:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-01-15T12:00:00")
    private LocalDateTime updateTime;

    @Schema(description = "是否发布：0-否，1-是", example = "1")
    private Short isPublished;
}