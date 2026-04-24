package com.jacolp.pojo.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicListVO implements Serializable {

    private Long id;

    private String topicName;

    private Integer sortOrder;

    private Long noteCount;

    private Short isPass;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}