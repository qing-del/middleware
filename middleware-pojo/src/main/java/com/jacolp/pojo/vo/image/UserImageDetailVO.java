package com.jacolp.pojo.vo.image;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户端图片详情响应 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户端图片详情信息")
public class UserImageDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    @Schema(description = "图片ID")
    private Long id;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String filename;

    /**
     * 图片访问URL
     */
    @Schema(description = "图片访问URL")
    private String ossUrl;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 上传时间
     */
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    /**
     * 是否公开
     */
    @Schema(description = "是否公开")
    private Short isPublic;
}