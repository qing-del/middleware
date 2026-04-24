package com.jacolp.pojo.vo;

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

    private Short isPublished;

    private Integer storageType;

    private Short isMissingInfo;

    private Short isPass;

    private Short isDeleted;

    private Long mdFileSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}