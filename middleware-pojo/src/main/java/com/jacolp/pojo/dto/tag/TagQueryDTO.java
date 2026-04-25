package com.jacolp.pojo.dto.tag;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagQueryDTO implements Serializable {

    private Long userId;

    private String keyword;

    private Integer pageNum;

    private Integer pageSize;
}