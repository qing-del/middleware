package com.jacolp.pojo.dto.user;

import com.jacolp.constant.UserConstant;
import com.jacolp.pojo.provider.RoleIdProvider;
import com.jacolp.pojo.provider.UsernameAndPasswordProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin 端新增用户请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAddDTO implements RoleIdProvider, UsernameAndPasswordProvider {

    @NotBlank(message = "用户名不能为空")
    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    private String username;

    @Size(min = UserConstant.PASSWORD_MIN_LENGTH, max = UserConstant.PASSWORD_MAX_LENGTH, message = "密码长度必须在 6 - 60 之间")
    private String password;

    @Positive(message = "角色ID必须为正数")
    private Long roleId;

    @Size(min = UserConstant.USERNAME_MIN_LENGTH, max = UserConstant.USERNAME_MAX_LENGTH, message = "用户名长度必须在 4 - 50 之间")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** 账号状态（选填，默认1正常启用） */
    private Integer status;
}
