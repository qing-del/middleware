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

    @Schema(description = "图片ID", example = "1")
    private Long id;

    @Schema(description = "文件名", example = "example.png")
    private String filename;

    @Schema(description = "图片访问URL", example = "https://oss.example.com/image/1/xxx.png")
    private String ossUrl;

    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @Schema(description = "上传时间", example = "2024-01-15T10:30:00")
    private LocalDateTime uploadTime;

    @Schema(description = "是否公开：0-否，1-是", example = "1")
    private Short isPublic;
}