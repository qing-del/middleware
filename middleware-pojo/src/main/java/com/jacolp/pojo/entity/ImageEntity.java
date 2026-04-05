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

    private Long topicId;  // 所属主题ID 0-未归类主题

    private String filename;  // 文件名

    private String ossUrl;

    private Short storageType;  // 存储方式：0-本地, 1-云OSS

    private Integer fileSize;

    private LocalDateTime uploadTime;
}
