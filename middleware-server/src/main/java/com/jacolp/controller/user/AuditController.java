package com.jacolp.controller.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-AuditController")
@RequestMapping("/user/audit")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-审核管理", description = "用户端审核管理接口")
public class AuditController {
}
