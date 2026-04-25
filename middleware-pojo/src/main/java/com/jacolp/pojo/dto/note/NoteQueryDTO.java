package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long topicId;

    private String title;

    private Short isPublished;

    private Short isPass;

    private Short isMissingInfo;

    private Integer pageNum;

    private Integer pageSize;
}