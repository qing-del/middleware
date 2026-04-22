package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteChangeDiffEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long noteId;

    private Integer status;

    private String diffJson;

    private Long oldFileSize;

    private Long newFileSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}