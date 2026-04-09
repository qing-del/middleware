package com.jacolp.pojo.domain;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片删除前验证 - 笔记引用数 DO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageNoteCountDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long imageId;

    private String filename;

    private Integer refCount;  // 引用次数
}
