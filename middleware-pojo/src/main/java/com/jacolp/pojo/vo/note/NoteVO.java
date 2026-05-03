package com.jacolp.pojo.vo.note;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long topicId;

    private String topicName;

    private String title;

    private String description;

    private Integer storageType;

    private Short status;  // 笔记状态

    private Integer missingInfoMask;  // 缺失信息掩码

    private Integer missingCount;  // 缺失信息数量

    private Long mdFileSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}