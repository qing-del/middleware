package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ImageMappingBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long mappingId;

    private Long imageId;
}
