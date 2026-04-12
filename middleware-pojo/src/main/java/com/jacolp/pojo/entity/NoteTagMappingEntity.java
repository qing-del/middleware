package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记与标签多对多关联表 biz_note_tag_mapping 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagMappingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long noteId;

    private Integer isDeleted;

    private Long tagId;

    private LocalDateTime createTime;
}
