package com.jacolp.service.impl;

import com.jacolp.constant.UserConstant;
import com.jacolp.exception.*;
import com.jacolp.utils.JwtUtil;
import com.jacolp.properties.JwtProperties;
import com.jacolp.utils.PasswordEncoder;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private UserMapper userMapper;

    @Override
    public String login(UserLoginDTO userLoginDTO) {
        // 通过用户名查询用户
        UserEntity user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            log.error("User isn't existed!");
            throw new NotFindUserException(UserConstant.NOT_FIND_USER);
        }

        // 检查密码
        boolean valid = passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword());
        if (!valid) {
            log.error("Password isn't correct!");
            throw new PasswordIncorrectException(UserConstant.USER_PASSWORD_ERROR);
        }

        // 生成 JWT 令牌（封装 id 到令牌里面）
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.ADMIN_ID_CLAIM, user.getId());
        return JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
    }
}
