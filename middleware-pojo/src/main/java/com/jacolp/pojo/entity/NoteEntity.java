package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记存储记录表 biz_note 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String title;

    private String htmlFilePath;

    private String mdFilePath;

    private Integer isPublished;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
