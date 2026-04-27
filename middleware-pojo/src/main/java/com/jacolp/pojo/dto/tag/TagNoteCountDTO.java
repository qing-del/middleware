package com.jacolp.pojo.dto.tag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagNoteCountDTO {

    private Long tagId;

    private String tagName;

    private Long noteCount;
}