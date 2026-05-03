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
public class
NoteEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long topicId;   // 所属主题ID，NULL 表示未分类

    private String title;

    private String description;

    private Integer storageType;  // 存储方式：0-本地存储, 1-阿里云OSS, 2-Cloudflare R2

    private Short status;  // 笔记状态：0-已创建,1-缺失信息,2-待转换,3-已转换,4-审核中,5-已通过,6-已公开,7-已拒绝,8-已删除

    private Integer missingInfoMask;  // 缺失信息掩码：1-标签,2-图片,4-内联笔记

    private Integer missingCount;  // 缺失信息数量，为0时自动扫描并触发状态转换

    private Long mdFileSize;  // MD文件大小合计(字节)

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}