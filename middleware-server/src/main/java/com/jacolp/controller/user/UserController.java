package com.jacolp.controller.user;

import java.util.HashMap;
import java.util.Map;

import com.jacolp.service.UserUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserProfileUpdateDTO;
import com.jacolp.pojo.dto.user.UserRegisterDTO;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.pojo.vo.user.UserOverviewVO;
import com.jacolp.result.Result;
import com.jacolp.utils.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-UserController")
@RequestMapping("/user/user")
@Slf4j
@Schema(description = "User - 用户管理")
@CrossOrigin("*")
@Validated
@Tag(name = "User-用户认证", description = "用户注册与登录接口")
public class UserController {
    @Autowired private UserUserService userUserService;

    @PostMapping("/login")
    @Operation(summary = "用户登录",
            description = "先校验用户名和密码是否匹配，登录成功后将用户 ID 写入 JWT claims 并签发令牌返回；后续接口会通过该 token 解析当前用户。")
    public Result<String> login(
            @Parameter(description = "用户登录请求，包含用户名和密码") @RequestBody @Valid UserLoginDTO userLoginDTO) {
        log.info("User login: {}", userLoginDTO.getUsername());
        return Result.success(userUserService.loginUser(userLoginDTO));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录",
            description = "用户退出；删除 Redis 中的 JWT 令牌。")
    public Result logout() {
        log.info("User logout, userId: {}", BaseContext.getCurrentId());
        userUserService.logout();
        return Result.success();
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册",
            description = "创建普通用户账号前会先校验入参合法性，并在服务层完成账号初始化、默认角色设置和密码落库，返回注册结果。")
    public Result<String> register(
            @Parameter(description = "用户注册请求，包含用户名、密码和基本信息") @RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        log.info("User register: {}", userRegisterDTO.getUsername());
        return Result.success(userUserService.register(userRegisterDTO));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息",
            description = "从 JWT 中解析当前用户ID，查询并返回用户详情（不含密码等敏感字段）。")
    public Result<UserDetailVO> getCurrentUser() {
        log.info("User get current user info");
        return Result.success(userUserService.getCurrentUser());
    }

    @GetMapping("/overview")
    @Operation(summary = "获取用户概览",
            description = "返回当前用户的基本信息，不包含资源统计数据。")
    public Result<UserOverviewVO> getOverview() {
        log.info("User get overview");
        return Result.success(userUserService.getUserOverview());
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户信息",
            description = "仅允许修改当前登录用户自身的昵称、邮箱等资料字段；修改密码可以复用这个接口")
    public Result<String> updateCurrentUser(
            @Parameter(description = "用户资料更新请求（昵称、邮箱等可修改字段）") @RequestBody @Valid UserProfileUpdateDTO dto) {
        log.info("User update profile");
        userUserService.updateCurrentUserProfile(dto);
        return Result.success("更新成功");
    }

    @DeleteMapping("/me")
    @Operation(summary = "删除账户（软删除）",
            description = "将当前登录用户账户状态更新为软删除状态，保留历史数据，避免物理删除造成关联记录丢失。")
    public Result<String> deleteCurrentUser() {
        log.info("User soft-delete account");
        userUserService.deleteCurrentUser();
        return Result.success("账户已删除");
    }

    @GetMapping("/getActivatedToken")
    @Operation(summary = "获取激活码",
            description = "用户注册之后，填写好邮箱（开发阶段暂时不用），即可接受激活码")
    public Result<String> getActivatedToken() {
        Long userId = BaseContext.getCurrentId();
        log.info("User get activated token, userId: {}", userId);

        // TODO 后续改造成发送邮件
        return Result.success(userUserService.getActiveAccountToken(userId));
    }


    /**
     * 用户激活
     * @param token 激活码
     * @return 激活结果
     */
    @GetMapping("/active/{token}")  // 直接点击只能是发送 GET 请求
    @Operation(summary = "用户激活",
            description = "用户注册成功后，会通过邮件发送激活链接，点击链接后调用该接口完成用户激活。")
    public Result<String> active(@PathVariable String token) {
        log.info("User active: {}", token);
        return Result.success(userUserService.activeAccount(BaseContext.getCurrentId()));
    }
}
