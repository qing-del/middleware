package com.jacolp.module.system.biz.web.controller.authorization;

import com.jacolp.module.system.biz.application.authorization.InternalLogoutService;
import com.jacolp.result.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the internal USER and ADMIN current-session logout endpoint. */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(prefix = "jacolp.oauth2.rs256", name = "enabled", havingValue = "true")
public class InternalLogoutController {
    private final InternalLogoutService logoutService;

    public InternalLogoutController(InternalLogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        logoutService.logout();
        return Result.success();
    }
}
