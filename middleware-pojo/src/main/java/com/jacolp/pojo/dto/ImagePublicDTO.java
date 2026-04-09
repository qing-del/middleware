package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开/取消公开图片 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagePublicDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;  // 图片ID，必填
}
