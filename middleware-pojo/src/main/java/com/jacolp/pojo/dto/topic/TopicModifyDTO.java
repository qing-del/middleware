package com.jacolp.pojo.dto.topic;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicModifyDTO implements Serializable {

    private Long id;

    private String topicName;

    private Integer sortOrder;
}