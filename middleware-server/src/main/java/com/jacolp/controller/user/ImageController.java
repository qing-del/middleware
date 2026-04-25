package com.jacolp.controller.user;

import com.jacolp.context.BaseContext;
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
 * <p>提供用户端的图片上传、查询、删除等功能接口</p>
 */
@RestController("User-ImageController")
@RequestMapping("/user/image")
@Slf4j
@Tag(name = "User-图片管理", description = "用户端图片管理接口")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private UserImageService userImageService;

    /**
     * 条件查询图片列表
     * <p>查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。</p>
     *
     * @param dto 查询条件，包含主题ID、文件名、分页参数等
     * @return 分页后的图片列表
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询图片列表", description = "查询当前用户自己的图片 + 别人已公开的图片。支持按主题 ID、文件名筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserImageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list images, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(imageService.listUserImages(userId, dto));
    }

    /**
     * 发起图片审核申请
     * <p>传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。</p>
     *
     * @param id 图片ID
     * @return 审核申请提交结果
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起图片审核申请", description = "传入图片 ID，发起对该图片的审核申请。仅允许申请审核自己的图片，且该图片不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit image audit, imageId: {}", id);
        imageService.submitImageAudit(id);
        return Result.success("审核申请已提交");
    }

    /**
     * 上传图片
     * <p>上传图片到云存储（阿里云OSS）。会自动校验文件大小是否在用户剩余配额内，并记录图片元数据。</p>
     *
     * @param file 图片文件，支持 jpg、png、gif、webp 等格式
     * @param topicId 所属主题ID（可选），用于分类管理
     * @return 上传后的图片详情，包含访问URL和元数据
     */
    @PostMapping
    @Operation(summary = "上传图片", description = "上传图片到云存储（阿里云OSS）。会自动校验文件大小是否在用户剩余配额内，并记录图片元数据。")
    public Result<UserImageDetailVO> upload(
            @Parameter(description = "图片文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "所属主题ID（可选）")
            @RequestParam(value = "topicId", required = false) Long topicId) {
        log.info("User upload image: {}, topicId: {}", file.getOriginalFilename(), topicId);
        UserImageDetailVO result = userImageService.uploadImage(file, topicId);
        return Result.success(result);
    }

    /**
     * 获取图片详情
     * <p>根据图片ID查询图片记录，返回图片访问URL、文件名、大小、上传时间等元数据信息。</p>
     *
     * @param id 图片ID
     * @return 图片详情信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取图片详情", description = "根据图片ID查询图片记录，返回图片访问URL、文件名、大小、上传时间等元数据信息。")
    public Result<UserImageDetailVO> getDetail(
            @Parameter(description = "图片ID")
            @PathVariable Long id) {
        log.info("User get image detail: {}", id);
        UserImageDetailVO result = userImageService.getImageDetail(id);
        return Result.success(result);
    }

    /**
     * 删除图片
     * <p>根据图片ID删除图片。从云存储删除对应对象文件，并从数据库删除图片记录。仅允许删除自己的图片。</p>
     *
     * @param id 图片ID
     * @return 删除结果提示
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除图片", description = "根据图片ID删除图片。从云存储删除对应对象文件，并从数据库删除图片记录。仅允许删除自己的图片。")
    public Result<String> delete(
            @Parameter(description = "图片ID")
            @PathVariable Long id) {
        log.info("User delete image: {}", id);
        userImageService.deleteImage(id);
        return Result.success("删除成功");
    }
}