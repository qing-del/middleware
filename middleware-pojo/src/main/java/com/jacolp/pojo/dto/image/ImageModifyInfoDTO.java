package com.jacolp.pojo.dto.image;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改图片信息（改名/换主题）DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageModifyInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;  // 图片ID，必填

    private String filename;  // 新文件名，可选

    private Long topicId;  // 新主题ID，可选
}
