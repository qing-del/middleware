package com.jacolp.pojo.vo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记双链映射简要信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteEachSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long targetNoteId;

    private String targetNoteTitle;

    private String parsedNoteName;

    private String anchor;

    private String nickname;

    private Short isMissing;
}