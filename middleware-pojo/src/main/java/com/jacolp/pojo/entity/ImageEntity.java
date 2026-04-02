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

    private String uniqueFilename;

    private String ossUrl;

    private Integer fileSize;

    private LocalDateTime uploadTime;
}
