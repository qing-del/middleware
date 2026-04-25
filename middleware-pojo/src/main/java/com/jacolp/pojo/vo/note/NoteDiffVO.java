package com.jacolp.pojo.vo.note;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> addedTags;

    private List<String> removedTags;

    private List<String> addedImages;

    private List<String> removedImages;

    private List<String> addedNoteNames;

    private List<String> removedNoteNames;
}