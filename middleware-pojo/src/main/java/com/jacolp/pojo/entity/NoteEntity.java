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

    private Long topicId;   // 所属主题ID，NULL 表示未分类

    private String title;

    private String description;

    private Short isPublished;  // 是否发布：1-公开, 0-私密

    private Integer storageType;  // 存储方式：0-本地存储, 1-阿里云OSS, 2-Cloudflare R2

    private Short isMissingInfo;  // 是否缺少标签/图片绑定：0-正常, 1-缺失

    private Short isPass;  // 审核状态：0-待审核, 1-已通过, 2-已拒绝

    private Short isDeleted;  // 是否删除：0-正常, 1-删除

    private Long mdFileSize;  // MD文件大小合计(字节)

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}