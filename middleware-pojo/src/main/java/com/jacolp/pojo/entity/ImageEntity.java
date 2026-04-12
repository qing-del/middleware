package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片资源映射表 biz_image 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long topicId;  // 所属主题ID，NULL 表示未归类

    private String filename;  // 文件名

    private String ossUrl;

    private Short storageType;  // 存储方式：1-阿里云OSS, 2-Cloudflare R2

    private Long fileSize;

    private Short isPublic;

    private Short isPass;

    private LocalDateTime uploadTime;
}
