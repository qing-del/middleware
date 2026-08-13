package com.jacolp.module.system.biz.web.controller.authorization;

import com.jacolp.module.system.biz.application.authorization.InternalLoginService;
import com.jacolp.module.system.biz.application.authorization.model.InternalIssuedTokens;
import com.jacolp.module.system.biz.application.authorization.model.InternalLoginRequest;
import com.jacolp.module.system.biz.application.dto.authorization.InternalLoginHttpRequest;
import com.jacolp.module.system.biz.application.dto.authorization.InternalTokenHttpResponse;
import com.jacolp.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Handles the internal USER and ADMIN password or email-code login endpoint. */
@RestController
@RequestMapping("/auth")
public class InternalAuthController {

    static final String INVALID_REQUEST_MESSAGE = "Invalid internal login request";

    private final InternalLoginService loginService;

    public InternalAuthController(InternalLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public Result<InternalTokenHttpResponse> login(
            @RequestBody InternalLoginHttpRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        InternalLoginRequest loginRequest;
        try {
            if (request == null) {
                throw new IllegalArgumentException("Internal login request is required");
            }
            loginRequest = request.toDomain(servletRequest.getRemoteAddr());
        } catch (IllegalArgumentException exception) {
            servletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return Result.error(INVALID_REQUEST_MESSAGE);
        }

        InternalIssuedTokens tokens = loginService.login(loginRequest);
        return Result.success(InternalTokenHttpResponse.from(tokens));
    }
}
