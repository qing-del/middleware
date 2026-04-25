package com.jacolp.controller.user;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.image.UserImageDeleteDTO;
import com.jacolp.pojo.dto.image.UserImageDetailDTO;
import com.jacolp.pojo.dto.image.UserImageQueryDTO;
import com.jacolp.pojo.vo.image.UserImageDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.ImageService;
import com.jacolp.service.UserImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端图片控制器
 */
@RestController("User-ImageController")
@RequestMapping("/user/image")
@Slf4j
@Schema(description = "User - 图片管理")
@Tag(name = "User-图片管理", description = "用户端图片条件查询与审核申请接口")
public class ImageController {

    @Autowired private ImageService imageService;

    @Autowired private UserImageService userImageService;

    /**
     * 条件查询图片
     * <p>查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。</p>
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询图片",
            description = "查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserImageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list images, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(imageService.listUserImages(userId, dto));
    }

    /**
     * 发起图片审核申请
     * <p>传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。</p>
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起图片审核申请",
            description = "传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit image audit, imageId: {}", id);
        imageService.submitImageAudit(id);
        return Result.success();
    }

    // ==================== 以下为新增的用户端图片管理接口 ====================

    /**
     * 上传图片
     * <p>校验文件大小是否在用户剩余配额内，上传到云存储并记录元数据</p>
     */
    @PostMapping
    @Operation(summary = "上传图片",
            description = "上传图片到云存储，校验文件大小是否在用户剩余配额内")
    public Result<UserImageDetailVO> upload(
            @Parameter(description = "图片文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "主题ID（可选）")
            @RequestParam(value = "topicId", required = false) Long topicId) {
        log.info("User upload image: {}, topicId: {}", file.getOriginalFilename(), topicId);
        UserImageDetailVO result = userImageService.uploadImage(file, topicId);
        return Result.success(result);
    }

    /**
     * 获取图片详情
     * <p>根据图片ID查询图片记录，返回图片访问URL和元数据信息</p>
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取图片详情",
            description = "根据图片ID查询图片记录，返回图片访问URL和元数据信息")
    public Result<UserImageDetailVO> getDetail(@PathVariable Long id) {
        log.info("User get image detail: {}", id);
        UserImageDetailVO result = userImageService.getImageDetail(id);
        return Result.success(result);
    }

    /**
     * 删除图片
     * <p>从云存储删除对应对象文件，并从数据库删除图片记录</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除图片",
            description = "从云存储删除对应对象文件，并从数据库删除图片记录")
    public Result<String> delete(@PathVariable Long id) {
        log.info("User delete image: {}", id);
        userImageService.deleteImage(id);
        return Result.success("删除成功");
    }
}