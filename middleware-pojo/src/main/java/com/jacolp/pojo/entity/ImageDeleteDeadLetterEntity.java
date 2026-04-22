package com.jacolp.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图片删除死信队列表 biz_image_delete_dead_letter 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDeleteDeadLetterEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String imageUrl;

    private Short status;  // 删除状态：0-等待删除, 1-删除完成

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
