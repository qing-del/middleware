package com.jacolp.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.pojo.dto.UserTagQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-TagController")
@RequestMapping("/user/tag")
@Slf4j
@Schema(description = "User - 标签管理")
@Tag(name = "User-标签管理", description = "用户端标签查询与标签审核申请接口")
public class TagController {

    @Autowired
    private TagService tagService;

    @PostMapping("/list")
    @Operation(summary = "条件查询标签",
            description = "查询范围为“当前用户自己的标签 + 其他用户已通过审核的标签”；支持关键词模糊匹配并按分页参数返回。")
    public Result<PageResult> list(@RequestBody UserTagQueryDTO dto) {
        log.info("User list tags, keyword: {}", dto.getKeyword());
        return Result.success(tagService.listUserTags(dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起标签审核申请",
            description = "提交标签审核申请前会校验：标签 ID 合法、标签归属当前用户、标签尚未通过审核，且不存在同标签的待审核申请。")
    public Result<String> submitAudit(@Parameter(description = "标签 ID") @RequestParam Long id) {
        log.info("User submit tag audit, tagId: {}", id);
        tagService.submitTagAudit(id);
        return Result.success();
    }
}
