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

    private Short status;  // 笔记状态

    private LocalDateTime createTime;
}
