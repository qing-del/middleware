package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息表 sys_user 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private Long roleId;    // 角色ID：1-创建者，2-管理员，3-普通用户，4-VIP用户

    private Integer status;  // 状态：1-正常, 0-禁用

    private Long maxStorageBytes;  // 用户个性化最大存储空间(字节)

    private Long noteUsedStorageBytes;  // 用户当前已用笔记存储空间(字节)

    private Long imageUsedStorageBytes;  // 用户当前已用图片存储空间(字节)

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
