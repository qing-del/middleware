package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.Data;

@Data
public class EachMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long noteId;
}
