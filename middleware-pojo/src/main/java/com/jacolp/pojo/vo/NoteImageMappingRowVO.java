package com.jacolp.pojo.vo;

import java.io.Serializable;

import lombok.Data;

@Data
public class NoteImageMappingRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long noteId;

    private Long imageId;

    private String parsedImageName;

    private String filename;

    private Short isCrossUser;

    private Short isPass;

    private Short isMissing;
}
