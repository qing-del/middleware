package com.jacolp.service;

import com.jacolp.pojo.dto.user.UserAddDTO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserModifyDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.result.PageResult;

import java.util.List;

public interface UserService {
    // 管理端登录，返回通过校验后的用户信息
    UserEntity loginAdmin(UserLoginDTO userLoginDTO);

    // 用户端登录，返回通过校验后的用户信息
    UserEntity loginUser(UserLoginDTO userLoginDTO);

    String register(UserRegisterDTO userRegisterDTO);

    PageResult list(UserListDTO userListDTO);

    // 管理员修改用户信息（权限/密码/基本信息）
    void modifyUser(UserModifyDTO userModifyDTO);

    // 管理员新增账户（只能赋予低于自身的角色）
    void addUser(UserAddDTO userAddDTO);

    // 管理员批量删除账户（含权限一票否决制）
    void deleteUsers(List<Long> ids);

    // 管理员封禁/解封账号状态
    void updateStatus(Long targetId, Integer status);

    // 获取用户信息
    UserEntity getUserById(Long id);

    // 用户端：获取当前登录用户详情（不含密码）
    UserDetailVO getCurrentUser();

    /** 用户端：更新当前登录用户的个人资料 */
    void updateCurrentUserProfile(UserProfileUpdateDTO dto);

    /** 用户端：软删除当前登录用户账户 */
    void deleteCurrentUser();

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
