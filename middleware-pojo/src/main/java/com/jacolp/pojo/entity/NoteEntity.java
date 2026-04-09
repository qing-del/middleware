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

    private Long topicId;   // 0-未分类到对应主题(default)

    private String title;

    private String description;

    private String htmlFilePath;

    private String mdFilePath;

    private Integer isPublished;  // 是否发布：1-公开, 0-私密

    private Integer storageType;  // 存储方式：1-阿里云OSS, 2-Cloudflare R2

    private Integer isMissingPhoto;  // 是否缺少图片：0-正常, 1-缺少图片

    private Short isPass;  // 审核状态：0-待审核, 1-已通过, 2-已拒绝

    private Integer isDeleted;  // 是否删除：0-正常, 1-删除

    private Long fileSize;  // HTML+MD文件大小合计(字节)

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}