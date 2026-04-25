package com.jacolp.pojo.dto.tag;

import java.io.Serializable;

import lombok.Data;

@Data
public class TagMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long tagId;
}
