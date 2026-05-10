package com.jacolp.pojo.dto.topic;

import java.io.Serializable;

import com.jacolp.pojo.provider.PageParamProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicListDTO implements Serializable, PageParamProvider {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String keyword;

    private Integer pageNum;

    private Integer pageSize;

    private String sortBy;
}