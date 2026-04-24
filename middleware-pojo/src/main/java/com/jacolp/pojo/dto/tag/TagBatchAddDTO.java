package com.jacolp.pojo.dto.tag;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagBatchAddDTO implements Serializable {

    private List<String> tagNames;
}