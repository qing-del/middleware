package com.jacolp.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.UserImageQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.ImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-ImageController")
@RequestMapping("/user/image")
@Slf4j
@Schema(description = "User - 图片管理")
@Tag(name = "User-图片管理", description = "用户端图片查询与图片审核申请接口")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PostMapping("/list")
    @Operation(summary = "条件查询图片",
            description = "查询范围为“当前用户自己的图片 + 其他用户已公开的图片”；支持按主题 ID、文件名筛选并按分页参数返回列表。")
    public Result<PageResult> list(@RequestBody UserImageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list images, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(imageService.listUserImages(userId, dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起图片审核申请",
            description = "提交图片审核申请前会校验：图片 ID 合法、图片存在且归属于当前用户、图片尚未通过审核，且当前不存在待审核申请。")
    public Result<String> submitAudit(@Parameter(description = "图片 ID") @RequestParam Long id) {
        log.info("User submit image audit, imageId: {}", id);
        imageService.submitImageAudit(id);
        return Result.success();
    }
}
