package com.jacolp.controller.user;

import com.jacolp.constant.UserConstant;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.properties.JwtProperties;
import com.jacolp.result.Result;
import com.jacolp.service.UserService;
import com.jacolp.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController("User-UserController")
@RequestMapping("/user/user")
@Slf4j
@Schema(description = "User - 用户管理")
@Tag(name = "User-用户认证", description = "用户注册与登录接口")
public class UserController {
    @Autowired private JwtProperties jwtProperties;
    @Autowired private UserService userService;

    @PostMapping("/login")
    @Operation(summary = "用户登录",
            description = "先校验用户名和密码是否匹配，登录成功后将用户 ID 写入 JWT claims 并签发令牌返回；后续接口会通过该 token 解析当前用户。")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("User login: {}", userLoginDTO.getUsername());

        // 先校验账号和密码，返回登录成功的用户信息
        UserEntity user = userService.loginUser(userLoginDTO);

        // 将用户ID写入 JWT，后续请求会通过拦截器解析出来
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.USER_ID_CLAIM, user.getId());
        String jwt = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        return Result.success(jwt);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册",
            description = "创建普通用户账号前会先校验入参合法性，并在服务层完成账号初始化、默认角色设置和密码落库，返回注册结果。")
    public Result<String> register(@RequestBody UserRegisterDTO userRegisterDTO) {
        log.info("User register: {}", userRegisterDTO.getUsername());
        return Result.success(userService.register(userRegisterDTO));
    }
}
