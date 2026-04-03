package com.jacolp.controller.admin;

import com.jacolp.pojo.dto.UserLoginDTO;
import com.jacolp.result.Result;
import com.jacolp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/user")
@Slf4j
@Schema(description = "用户管理")
public class UserController {

    @Autowired private UserService userService;

    @PostMapping("/login")
    @Operation(description = "用户登录")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("User login: {}", userLoginDTO.getUsername());
        return Result.success(userService.login(userLoginDTO));
    }
}
