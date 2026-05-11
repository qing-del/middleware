package com.jacolp.controller.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-AuditController")
@RequestMapping("/user/audit")
@Slf4j
@Schema(description = "User - 审核管理")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-审核管理", description = "用户端审核管理接口（预留，后续解耦时转移）")
public class AuditController {
}
