package com.jacolp.pojo.vo.topic;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicDetailVO implements Serializable {

    private Long id;

    private String topicName;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}