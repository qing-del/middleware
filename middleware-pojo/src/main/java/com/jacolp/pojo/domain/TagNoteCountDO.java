package com.jacolp.pojo.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagNoteCountDO {

    private Long tagId;

    private String tagName;

    private Long noteCount;
}