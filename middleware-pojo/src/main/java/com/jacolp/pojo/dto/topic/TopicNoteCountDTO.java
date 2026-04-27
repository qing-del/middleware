package com.jacolp.pojo.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicNoteCountDTO {
    private String topicName;
    private Long noteCount;
}
