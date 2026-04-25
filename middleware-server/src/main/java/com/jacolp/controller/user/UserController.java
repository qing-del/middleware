package com.jacolp.controller.user;

import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.properties.JwtProperties;
import com.jacolp.result.Result;
import com.jacolp.service.UserService;
import com.jacolp.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息",
            description = "从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。")
    public Result<UserDetailVO> getCurrentUser() {
        log.info("User get current user info");
        return Result.success(userService.getCurrentUser());
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息",
            description = "仅允许修改当前登录用户自身的昵称、邮箱等资料字段；")
    public Result<String> updateCurrentUser(@RequestBody UserProfileUpdateDTO dto) {
        log.info("User update profile");
        userService.updateCurrentUserProfile(dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/me")
    @Operation(summary = "删除账户（软删除）",
            description = "将当前登录用户账户状态更新为软删除状态，保留历史数据，避免物理删除造成关联记录丢失。")
    public Result<String> deleteCurrentUser() {
        log.info("User soft-delete account");
        userService.deleteCurrentUser();
        return Result.success("账户已删除");
    }

    @GetMapping("/getActivatedToken")
    @Operation(summary = "获取激活码",
            description = "用户注册之后，填写好邮箱（开发阶段暂时不用），即可接受激活码")
    public Result<String> getActivatedToken() {
        Long userId = BaseContext.getCurrentId();
        log.info("User get activated token, userId: {}", userId);

        // 检查是否放行
        if (!userService.checkActivationStatus(userId)) {
            return Result.error(UserConstant.USER_ALREADY_ACTIVE);
        }

        // 放行之后生成激活使用的令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.ACTIVE_SIGN_KEY, true);
        claims.put(UserConstant.USER_ID_CLAIM, userId);

        // TODO 后续改造成发送邮件
        return Result.success(
                JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims)
        );
    }


    /**
     * 用户激活
     * @param token 激活码
     * @return 激活结果
     */
    @PostMapping("/active/{token}")
    @Operation(summary = "用户激活",
            description = "用户注册成功后，会通过邮件发送激活链接，点击链接后调用该接口完成用户激活。")
    public Result<String> active(@PathVariable String token) {
        log.info("User active: {}", token);
        Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
        } catch (Exception e) {
            log.error("Failed to parse JWT: {}", e.getMessage());
            return Result.error(UserConstant.JWT_NOT_VALID);
        }
        if (claims == null
                || claims.get(UserConstant.ACTIVE_SIGN_KEY) == null
                || !claims.get(UserConstant.ACTIVE_SIGN_KEY).equals(true)) {
            return Result.error(UserConstant.JWT_NOT_VALID);
        }

        Long userId = (Long) claims.get(UserConstant.USER_ID_CLAIM);

        return Result.success(userService.activeAccount(userId));
    }
}
