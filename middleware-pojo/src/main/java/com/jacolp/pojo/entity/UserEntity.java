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

    private Long roleId;    // 角色ID：1-创建者，2-管理员，3-普通用户，4-VIP用户

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
