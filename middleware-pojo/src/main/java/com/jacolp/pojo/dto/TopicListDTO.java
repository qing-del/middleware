package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String keyword;

    private Integer pageNum;

    private Integer pageSize;

    private String sortBy;
}