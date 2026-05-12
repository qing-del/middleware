package com.jacolp.controller.admin;

import com.jacolp.annotation.RequireSuperiorRole;
import com.jacolp.constant.UserConstant;
import com.jacolp.pojo.dto.user.UserAddDTO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.dto.user.UserLoginDTO;
import com.jacolp.pojo.dto.user.UserModifyDTO;
import com.jacolp.pojo.dto.user.UserStatusDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.properties.JwtProperties;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.AdminUserService;
import com.jacolp.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController("Admin-UserController")
@RequestMapping("/admin/user")
@CrossOrigin("*")
@Slf4j
@Schema(description = "Admin - 用户管理")
@Tag(name = "Admin-用户管理", description = "管理员登录、用户增删改查与封禁/解封接口")
public class UserController {
    @Autowired private JwtProperties jwtProperties;
    @Autowired private AdminUserService adminUserService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "验证管理员账号密码，登录成功后签发 JWT 令牌并返回；后续管理员接口通过该 token 鉴权。")
    public Result<String> login(
            @Parameter(description = "管理员登录请求，包含用户名和密码") @RequestBody UserLoginDTO userLoginDTO) {
        log.info("User login: {}", userLoginDTO.getUsername());

        UserEntity user = adminUserService.loginAdmin(userLoginDTO);

        if (user == null) {
            log.error("User login failed!");
            return Result.error(UserConstant.USER_LOGIN_FAILED);
        }

        // 生成 JWT 令牌（封装 id 到令牌里面）
        Map<String, Object> claims = new HashMap<>();
        claims.put(UserConstant.ADMIN_ID_CLAIM, user.getId());
        String jwt = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);

        return Result.success(jwt);
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询用户列表", description = "按用户名、角色等条件分页查询用户列表，返回分页结果供管理端展示。")
    public Result<PageResult> list(
            @Parameter(description = "用户列表查询条件，包含分页参数和筛选字段") @RequestBody UserListDTO userListDTO) {
        return Result.success(adminUserService.list(userListDTO));
    }

    @PutMapping("/user")
    @Operation(summary = "修改用户信息", description = "修改指定用户的基本信息、权限或密码，需要上级角色权限。")
    @RequireSuperiorRole
    public Result<String> modify(
            @Parameter(description = "用户修改请求，包含目标用户ID和需要修改的字段") @RequestBody UserModifyDTO userModifyDTO) {
        log.info("Admin modify user, target id: {}", userModifyDTO.getId());
        adminUserService.modifyUser(userModifyDTO);
        return Result.success();
    }

    @PostMapping("/user")
    @Operation(summary = "管理员新增账户", description = "管理员直接创建新用户账户，无需注册流程。")
    public Result<String> add(
            @Parameter(description = "新增用户请求，包含用户名、密码和角色信息") @RequestBody UserAddDTO userAddDTO) {
        log.info("Admin add user, username: {}", userAddDTO.getUsername());
        adminUserService.addUser(userAddDTO);
        return Result.success();
    }

    @DeleteMapping("/user")
    @Operation(summary = "批量删除账户", description = "按用户ID列表批量删除用户账户。")
    public Result<String> delete(
            @Parameter(description = "待删除的用户ID列表") @RequestParam List<Long> ids) {
        log.info("Admin delete users, ids: {}", ids);
        adminUserService.deleteUsers(ids);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "封禁/解封账号", description = "设置用户账号的启用/禁用状态，需要上级角色权限。")
    @RequireSuperiorRole
    public Result<String> updateStatus(
            @Parameter(description = "目标状态（1:启用, 0:禁用）") @PathVariable Integer status,
            @Parameter(description = "包含目标用户ID的请求体") @RequestBody UserStatusDTO userStatusDTO) {
        log.info("Admin update user status, target id: {}, status: {}", userStatusDTO.getId(), status);
        adminUserService.updateStatus(userStatusDTO.getId(), status);
        return Result.success();
    }

    @GetMapping("/user")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取单个用户的详细信息。")
    public Result<UserEntity> get(
            @Parameter(description = "用户ID") @RequestParam Long id) {
        log.info("Admin get user, id: {}", id);
        return Result.success(adminUserService.getUserById(id));
    }
}

