package com.jacolp.pojo.dto.image;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端图片条件查询 DTO。
 * 查询逻辑：当前用户自己的图片 + 别人已公开的图片。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserImageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long topicId;

    private String filename;

    private Integer pageNum;

    private Integer pageSize;
}
