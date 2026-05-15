package com.jacolp.service;

import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.pojo.vo.user.UserOverviewVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface UserUserService {
    // 用户端登录，返回通过校验后的用户信息
    UserEntity loginUser(@NotNull @Valid UserLoginDTO userLoginDTO);

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
     * 检查账户是否激活
     * <p>此接口用于检查是否放行发放用户激活码的</p>
     * @param userId 用户ID
     * @return 放行获取激活码返回 true，否则返回 false
     */
    boolean checkActivationStatus(Long userId);
}
