package com.jacolp.service;

import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.pojo.vo.user.UserOverviewVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface UserUserService {
    // 用户端登录，返回通过校验后的用户信息
    String loginUser(@NotNull @Valid UserLoginDTO userLoginDTO);

    void logout();

    String register(@NotNull @Valid UserRegisterDTO userRegisterDTO);
    // 用户端：获取当前登录用户详情（不含密码）

    UserDetailVO getCurrentUser();
    // 用户端：获取当前登录用户概览信息（不含ID）

    UserOverviewVO getUserOverview();

    /** 用户端：软删除当前登录用户账户 */
    void deleteCurrentUser();

    /** 用户端：更新当前登录用户的个人资料 */
    void updateCurrentUserProfile(@NotNull @Valid UserProfileUpdateDTO dto);

    /**
     * 激活账户
     * @param token 激活码
     * @return 激活结果
     */
    String activeAccount(Long token);

    /**
     * 发送激活邮件
     * @param userId 用户ID
     */
    void sendActivationEmail(Long userId);
}
