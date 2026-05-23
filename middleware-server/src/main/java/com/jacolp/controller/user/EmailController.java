package com.jacolp.controller.user;

import java.util.HashMap;
import java.util.Map;

import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.pojo.vo.user.UserDetailVO;
import com.jacolp.result.Result;
import com.jacolp.service.UserUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("User-EmailController")
@RequestMapping("/user/email")
@Slf4j
@CrossOrigin("*")
@Schema(description = "User - 邮箱管理")
@Tag(name = "User-邮箱管理", description = "用户邮箱绑定与激活邮件重发接口")
public class EmailController {
    @Autowired private UserUserService userUserService;

    @PostMapping("/resend-activation")
    @Operation(summary = "重新发送激活邮件",
            description = "向当前用户注册邮箱再次发送激活链接")
    public Result<String> resendActivation() {
        Long userId = BaseContext.getCurrentId();
        log.info("User request resend activation email, userId: {}", userId);
        userUserService.sendActivationEmail(userId);
        return Result.success("激活邮件已重新发送，请查收邮箱");
    }

    @GetMapping("/status")
    @Operation(summary = "获取邮箱与激活状态",
            description = "返回当前用户的邮箱地址与账号激活状态")
    public Result<Map<String, Object>> getEmailStatus() {
        Long userId = BaseContext.getCurrentId();
        UserDetailVO user = userUserService.getCurrentUser();
        Map<String, Object> status = new HashMap<>();
        status.put("email", user.getEmail());
        status.put("username", user.getUsername());
        boolean isActive = user.getStatus() != null && user.getStatus() == UserConstant.ACTIVE_STATUS;
        status.put("isActive", isActive);
        log.info("User email status query, userId: {}", userId);
        return Result.success(status);
    }
}
