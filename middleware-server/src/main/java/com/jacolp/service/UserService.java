package com.jacolp.service;

import com.jacolp.pojo.dto.UserAddDTO;
import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserModifyDTO;
import com.jacolp.pojo.dto.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
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
}
