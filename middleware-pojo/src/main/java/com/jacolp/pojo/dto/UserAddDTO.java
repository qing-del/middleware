package com.jacolp.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin 端新增用户请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAddDTO implements RoleIdProvider {

    /** 用户名（必填） */
    private String username;

    /** 密码（必填） */
    private String password;

    /** 角色 ID（必填，操作者只能赋予低于自身的角色） */
    private Long roleId;

    /** 昵称（选填，默认与用户名一致） */
    private String nickname;

    /** 邮箱（选填） */
    private String email;

    /** 账号状态（选填，默认1正常启用） */
    private Integer status;
}
