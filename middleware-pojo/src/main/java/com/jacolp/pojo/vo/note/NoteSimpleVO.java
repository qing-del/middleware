package com.jacolp.pojo.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关联笔记简要信息 VO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private Short isCrossUser;

    private Short isPublished;  // TODO 后续可以选择是否加入

    private Short isDeleted;

    private LocalDateTime createTime;
}
