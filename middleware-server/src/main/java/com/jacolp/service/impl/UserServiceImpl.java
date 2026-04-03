package com.jacolp.service.impl;

import com.jacolp.constant.PageConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.constant.RoleConstant;
import com.jacolp.exception.*;
import com.jacolp.utils.PasswordEncoder;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.result.PageResult;
import com.jacolp.service.UserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;

    @Override
    public UserEntity loginAdmin(UserLoginDTO userLoginDTO) {
        // 通过用户名查询用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 检查账号状态
        if (user.getStatus() == 0) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }

        // 检查账号权限
        if (user.getRoleId() != RoleConstant.ADMIN
                && user.getRoleId() != RoleConstant.CREATOR) {
            log.error("User isn't admin!");
            throw new PasswordIncorrectException(UserConstant.PERMISSION_DENIED);
        }

        // 检查密码
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        return user;
    }

    @Override
    public UserEntity loginUser(UserLoginDTO userLoginDTO) {
        // 检查用户名是否为空
        if (!StringUtils.hasText(userLoginDTO.getUsername())) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }
        
        // 检查密码是否为空，避免后续密码比对出现无意义调用
        if (!StringUtils.hasText(userLoginDTO.getPassword())) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }

        // 1. 根据用户名查用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 2. 账号被禁用则直接拒绝登录
        if (user.getStatus() == 0) {
            log.error("User is banned!");
            throw new UserIsBanException(UserConstant.USER_IS_BANNED);
        }


        // 3. 校验密码是否一致
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        return user;
    }

    @Override
    public String register(UserRegisterDTO userRegisterDTO) {
        // 检查用户名是否为空
        if (!StringUtils.hasText(userRegisterDTO.getUsername())) {
            throw new BaseException(UserConstant.USERNAME_IS_REQUIRED);
        }

        // 检查密码是否为空
        if (!StringUtils.hasText(userRegisterDTO.getPassword()) || !StringUtils.hasText(userRegisterDTO.getConfirmPassword())) {
            throw new BaseException(UserConstant.PASSWORD_IS_REQUIRED);
        }

        // 检查两次密码是否一致
        if (!userRegisterDTO.getPassword().equals(userRegisterDTO.getConfirmPassword())) {
            throw new BaseException(UserConstant.PASSWORD_CONFIRM_ERROR);
        }

        // 检查是否存在相同用户名的用户
        UserEntity existed = userMapper.selectByUsername(userRegisterDTO.getUsername());
        if (existed != null) {
            throw new BaseException(UserConstant.USER_ALREADY_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(userRegisterDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setRoleId(RoleConstant.USER);
        user.setStatus(UserConstant.DEFAULT_STATUS);

        int count = userMapper.insertUser(user);
        if (count <= 0) {
            throw new BaseException("注册失败");
        }
        return "注册成功";
    }

    @Override
    public PageResult list(UserListDTO userListDTO) {
        // 如果没有传递参数则为查询全表
        if (userListDTO == null) {
            userListDTO = new UserListDTO();
        }

        // 如果没有传递分页参数，则默认为第一页
        Integer pageParam = userListDTO.getPage();
        Integer pageSizeParam = userListDTO.getPageSize();
        int page = pageParam == null || pageParam <= 0 ? PageConstant.DEFAULT_PAGE : pageParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? PageConstant.DEFAULT_PAGE_SIZE : pageSizeParam;
        PageHelper.startPage(page, pageSize);


        List<UserEntity> records = userMapper.listByCondition(userListDTO);
        PageInfo<UserEntity> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }
}
