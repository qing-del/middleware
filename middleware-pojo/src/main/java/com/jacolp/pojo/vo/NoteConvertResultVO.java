package com.jacolp.pojo.vo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteConvertResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private NoteConvertMetaVO meta;

    private String tocHtml;

    private String bodyHtml;
}