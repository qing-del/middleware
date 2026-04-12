package com.jacolp.controller.admin;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import com.jacolp.enums.StorageOperationType;
import com.jacolp.pojo.vo.ImageBatchDeleteVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.annotation.CheckAndUpdateUserStorage;
import com.jacolp.annotation.ImageLimit;
import com.jacolp.exception.BaseException;
import com.jacolp.pojo.dto.ImageAuditReviewDTO;
import com.jacolp.pojo.dto.ImageModifyInfoDTO;
import com.jacolp.pojo.dto.ImagePublicDTO;
import com.jacolp.pojo.dto.ImageQueryDTO;
import com.jacolp.pojo.vo.NoteSimpleVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.ImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin 端图片管理控制器。
 * 
 * 说明：
 * - 本控制器仅负责参数接收与返回封装。
 * - 核心业务规则统一下沉到 Service 层。
 * - AOP 切面在进入控制器方法前执行校验（文件大小、格式、存储配额等）。
 */
@RestController
@RequestMapping("/admin/image")
@Slf4j
@Schema(description = "Admin - 图片管理")
public class ImageController {

    @Autowired
    private ImageService imageService;

    /**
     * 5.1 上传图片。
     * 
     * AOP 链：@ImageLimit → @CheckAndUpdateUserStorage
     */
    @PostMapping("/upload")
    @ImageLimit
    @CheckAndUpdateUserStorage(operationType = StorageOperationType.UPLOAD)
    @Operation(description = "上传图片")
    public Result<String> upload(
            @RequestParam(required = false) Long topicId,
            @Parameter(description = "图片文件") @RequestParam MultipartFile file) {
        log.info("Admin upload image, topicId: {}, filename: {}", topicId, file.getOriginalFilename());
        imageService.uploadImage(file, topicId);
        return Result.success();
    }

    /**
     * 5.2 修改图片源文件。
     * 
     * AOP 链：@ImageLimit → @CheckAndUpdateUserStorage
     */
    @PutMapping("/modify-file")
    @ImageLimit
    @CheckAndUpdateUserStorage(operationType = StorageOperationType.UPLOAD)
    @Operation(description = "修改图片源文件")
    public Result<String> modifyFile(
            @Parameter(description = "图片ID") @RequestParam Long id,
            @Parameter(description = "新文件") @RequestParam MultipartFile file) {
        log.info("Admin modify image file, id: {}, filename: {}", id, file.getOriginalFilename());
        imageService.modifyImageFile(id, file);
        return Result.success();
    }

    /**
     * 5.3 修改图片信息（改名/换主题）。
     */
    @PutMapping("/modify-info")
    @Operation(description = "修改图片信息（改名/换主题）")
    public Result<String> modifyInfo(@RequestBody ImageModifyInfoDTO dto) {
        log.info("Admin modify image info, id: {}", dto.getId());
        imageService.modifyImageInfo(dto);
        return Result.success();
    }

    /**
     * 5.4 云厂商迁移入口（默认阿里云 OSS，R2 预留）。
     */
    @PutMapping("/transfer-to-cloud")
    @Operation(description = "云厂商迁移入口（默认阿里云 OSS，R2 预留）")
    public Result<String> transferToCloud(
            @Parameter(description = "图片ID列表，使用英文逗号分隔") @RequestParam String ids) {
        List<Long> idList = parseIds(ids);
        log.info("Admin transfer to cloud, ids: {}", idList);
        imageService.transferToCloud(idList);
        return Result.success();
    }

    /**
     * 5.5 已废弃：不再支持转为本地存储。
     */
    @PutMapping("/transfer-to-local")
    @Operation(description = "已废弃：本地存储方案已下线")
    public Result<String> transferToLocal(
            @Parameter(description = "图片ID列表，使用英文逗号分隔") @RequestParam String ids) {
        List<Long> idList = parseIds(ids);
        log.info("Admin transfer to local, ids: {}", idList);
        imageService.transferToLocal(idList);
        return Result.success();
    }

    /**
     * 5.6 批量删除图片。
     */
    @DeleteMapping("/delete")
    @Operation(description = "批量删除图片")
    public Result<ImageBatchDeleteVO> delete(
            @Parameter(description = "图片ID列表，使用英文逗号分隔") @RequestParam String ids) {
        List<Long> idList = parseIds(ids);
        log.info("Admin delete images, ids: {}", idList);
        return Result.success(imageService.deleteImages(idList));
    }

    /**
     * 5.7 获取图片列表。
     */
    @PostMapping("/list")
    @Operation(description = "分页查询图片列表")
    public Result<PageResult> list(@RequestBody ImageQueryDTO dto) {
        log.info("Admin list images, userId: {}, topicId: {}", dto.getUserId(), dto.getTopicId());
        return Result.success(imageService.listImages(dto));
    }

    /**
     * 5.8 查询图片关联的笔记列表。
     */
    @GetMapping("/notes/{imageId}")
    @Operation(description = "查询图片关联的笔记列表")
    public Result<List<NoteSimpleVO>> listNotes(
            @Parameter(description = "图片ID") @PathVariable Long imageId) {
        log.info("Admin list notes by image, imageId: {}", imageId);
        return Result.success(imageService.listNotesByImageId(imageId));
    }

    /**
     * 5.9 公开/取消公开图片。
     */
    @PostMapping("/public/{isPublic}")
    @Operation(description = "公开/取消公开图片")
    public Result<String> setPublic(
            @Parameter(description = "是否公开（0:私有, 1:公开）") @PathVariable Short isPublic,
            @RequestBody ImagePublicDTO dto) {
        log.info("Admin set image public, imageId: {}, isPublic: {}", dto.getId(), isPublic);
        imageService.setImagePublic(dto.getId(), isPublic);
        return Result.success();
    }

    /**
     * 5.10 审核图片（管理员）。
     */
    @PutMapping("/audit/review")
    @Operation(description = "审核图片（管理员）")
    public Result<String> auditReview(@RequestBody ImageAuditReviewDTO dto) {
        log.info("Admin audit review image, auditId: {}, approved: {}", dto.getAuditId(), dto.getApproved());
        imageService.auditReviewImage(dto);
        return Result.success();
    }



    // ============ 私有方法 ============

    /**
     * 解析 ID 字符串（逗号分隔）为 Long 列表。
     * 
     * 策略：
     * - 使用 LinkedHashSet 去重并保持顺序。
     * - 校验 ID 格式和有效性。
     */
    private List<Long> parseIds(String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            throw new BaseException("ID列表不能为空");
        }

        Set<Long> idSet = new LinkedHashSet<>();
        String[] parts = ids.split(",");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                Long id = Long.parseLong(trimmed);
                if (id <= 0) {
                    throw new BaseException("图片ID必须为正整数");
                }
                idSet.add(id);
            } catch (NumberFormatException e) {
                throw new BaseException("图片ID 格式不合法：" + trimmed);
            }
        }

        if (idSet.isEmpty()) {
            throw new BaseException("需要至少一个有效的ID");
        }

        return new java.util.ArrayList<>(idSet);
    }
}
