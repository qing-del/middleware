package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteModifyInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;   // TODO 让笔记信息修改不能改title(*)

    private String description;

    private Long topicId;
}