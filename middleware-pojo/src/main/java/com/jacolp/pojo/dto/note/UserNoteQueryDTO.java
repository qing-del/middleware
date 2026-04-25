package com.jacolp.pojo.dto.note;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户端笔记条件查询 DTO。
 * 查询逻辑：当前用户自己的笔记 + 别人已发布的笔记。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNoteQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long topicId;

    private String title;

    private Integer pageNum;

    private Integer pageSize;
}
