package com.jacolp.controller.admin;

import com.jacolp.pojo.dto.image.ImageAuditReviewDTO;
import com.jacolp.result.Result;
import com.jacolp.service.ImageAuditReviewCompatibilityService;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;

/** Keeps only the legacy review route until audit M3 owns it. */
@RestController
@RequestMapping("/admin/image")
@Slf4j
public class ImageAuditReviewCompatibilityController {
    private final ImageAuditReviewCompatibilityService service;
    public ImageAuditReviewCompatibilityController(ImageAuditReviewCompatibilityService service) { this.service = service; }
    @PutMapping("/audit/review")
    @Operation(summary = "审核图片", description = "管理员根据审核申请执行通过或拒绝；通过时将图片审核状态置为通过，拒绝时必须给出拒绝原因并同步回写审核记录。(开发的时候不要调用这个接口，因为在认证控制器里面已经有图片审核的接口了)")
    public Result<String> review(@Parameter(description = "图片审核请求（审核ID、是否通过、拒绝原因）") @RequestBody ImageAuditReviewDTO dto) {
        log.info("Admin audit review image, auditId: {}, approved: {}", dto.getAuditId(), dto.getApproved());
        service.review(dto);
        return Result.success();
    }
}
