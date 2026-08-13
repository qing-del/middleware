package com.jacolp.module.system.biz.web.controller.authorization;

import com.jacolp.module.system.biz.application.authorization.CoreAgentLogoutService;
import com.jacolp.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the authenticated CORE AGENT bearer-session logout endpoint. */
@RestController
public final class CoreAgentLogoutController {

    private final CoreAgentLogoutService logoutService;

    public CoreAgentLogoutController(CoreAgentLogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @PostMapping("/oauth/logout")
    public Result<Void> logout() {
        logoutService.logout();
        return Result.success();
    }
}
