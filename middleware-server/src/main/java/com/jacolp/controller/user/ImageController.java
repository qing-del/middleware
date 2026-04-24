package com.jacolp.controller.user;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.UserImageQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("User-ImageController")
@RequestMapping("/user/image")
@Slf4j
@Schema(description = "User - 图片管理")
@Tag(name = "User-图片管理", description = "用户端图片条件查询与审核申请接口")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PostMapping("/list")
    @Operation(summary = "条件查询图片",
            description = "查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserImageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list images, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(imageService.listUserImages(userId, dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起图片审核申请",
            description = "传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit image audit, imageId: {}", id);
        imageService.submitImageAudit(id);
        return Result.success();
    }
}
