package com.jacolp.pojo.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNoteCountDO {
    private String topicName;
    private Long noteCount;
}
