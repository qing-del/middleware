package com.jacolp.controller.admin;

import com.jacolp.annotation.RequireSuperiorRole;
import com.jacolp.constant.UserConstant;
import com.jacolp.pojo.dto.UserAddDTO;
import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.pojo.dto.UserModifyDTO;
import com.jacolp.pojo.dto.UserStatusDTO;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.properties.JwtProperties;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.UserService;
import com.jacolp.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController("Admin-UserController")
@RequestMapping("/admin/user")
@Slf4j
@Schema(description = "Admin - 用户管理")
public class UserController {
    @Autowired private JwtProperties jwtProperties;
    @Autowired private UserService userService;

    @PostMapping("/login")
    @Operation(description = "用户登录")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("User login: {}", userLoginDTO.getUsername());

        UserEntity user = userService.loginAdmin(userLoginDTO);

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
    @Operation(description = "分页查询用户列表")
    public Result<PageResult> list(@RequestBody UserListDTO userListDTO) {
        return Result.success(userService.list(userListDTO));
    }

    @PutMapping("/modify")
    @Operation(description = "修改用户信息（权限/密码/基本信息）")
    @RequireSuperiorRole
    public Result<String> modify(@RequestBody UserModifyDTO userModifyDTO) {
        log.info("Admin modify user, target id: {}", userModifyDTO.getId());
        userService.modifyUser(userModifyDTO);
        return Result.success();
    }

    @PostMapping("/add")
    @Operation(description = "管理员新增账户")
    public Result<String> add(@RequestBody UserAddDTO userAddDTO) {
        log.info("Admin add user, username: {}", userAddDTO.getUsername());
        userService.addUser(userAddDTO);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(description = "批量删除账户")
    public Result<String> delete(@RequestParam List<Long> ids) {
        log.info("Admin delete users, ids: {}", ids);
        userService.deleteUsers(ids);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(description = "封禁/解封账号")
    @RequireSuperiorRole
    public Result<String> updateStatus(@PathVariable Integer status,
                                       @RequestBody UserStatusDTO userStatusDTO) {
        log.info("Admin update user status, target id: {}, status: {}", userStatusDTO.getId(), status);
        userService.updateStatus(userStatusDTO.getId(), status);
        return Result.success();
    }
}

