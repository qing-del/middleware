package com.jacolp.pojo.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片列表查询条件 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;  // 用户ID，可选

    private Long topicId;  // 主题ID，可选

    private String filename;  // 文件名前缀搜索，可选

    private Short storageType;  // 存储方式（1:阿里云OSS, 2:Cloudflare R2），可选

    private Short isPublic;  // 是否公开（0:私有, 1:公开），可选

    private Short isPass;  // 审核状态（0:待审核, 1:已通过, 2:已拒绝），可选

    private Integer pageNum;  // 页码

    private Integer pageSize;  // 每页条数
}
