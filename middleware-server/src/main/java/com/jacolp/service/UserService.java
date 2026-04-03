package com.jacolp.service;

import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.result.PageResult;

public interface UserService {
    // 管理端登录，返回通过校验后的用户信息
    UserEntity loginAdmin(UserLoginDTO userLoginDTO);

    // 用户端登录，返回通过校验后的用户信息
    UserEntity loginUser(UserLoginDTO userLoginDTO);

    String register(UserRegisterDTO userRegisterDTO);

    PageResult list(UserListDTO userListDTO);
}
