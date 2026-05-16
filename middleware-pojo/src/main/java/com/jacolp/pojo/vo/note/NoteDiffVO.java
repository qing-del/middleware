package com.jacolp.pojo.vo.note;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记新旧信息
 * @author jacolp
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> oldTags;

    private List<String> newTags;

    private List<String> oldImages;

    private List<String> newImages;

    private List<String> oldNoteReflection;

    private List<String> newNoteReflection;
}