package com.jacolp.pojo.dto;

import com.jacolp.pojo.provider.RoleIdProvider;
import com.jacolp.pojo.provider.TargetUserProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员修改用户信息的请求参数。
 * 除 createTime / updateTime 之外包含 UserEntity 的所有可修改字段，
 * 另外额外携带 newPassword / confirmPassword 用于密码覆盖。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserModifyDTO implements TargetUserProvider, RoleIdProvider {

    /** 被修改用户的 ID（必填） */
    private Long id;

    private String username;

    private String nickname;

    private String email;

    private Long roleId;

    private Integer status;

    /** 新密码（选填，填了就覆盖旧密码） */
    private String newPassword;

    /** 确认密码（必须与 newPassword 一致） */
    private String confirmPassword;

    @Override
    public Long getTargetUserId() {
        return this.id;
    }
}
