package com.jacolp.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDeleteDeadLetterEntity {
    private Long id;
    private String imageUrl;
    private Short status;
    private Integer retryCount;
    private String createTime;
    private String updateTime;
}
