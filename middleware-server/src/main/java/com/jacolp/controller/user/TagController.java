package com.jacolp.controller.user;

import com.jacolp.pojo.dto.UserTagQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TagService;
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

@RestController("User-TagController")
@RequestMapping("/user/tag")
@Slf4j
@Schema(description = "User - 标签管理")
@Tag(name = "User-标签管理", description = "用户端标签条件查询与审核申请接口")
public class TagController {

    @Autowired
    private TagService tagService;

    @PostMapping("/list")
    @Operation(summary = "条件查询标签",
            description = "查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(@RequestBody UserTagQueryDTO dto) {
        log.info("User list tags, keyword: {}", dto.getKeyword());
        return Result.success(tagService.listUserTags(dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起标签审核申请",
            description = "传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit tag audit, tagId: {}", id);
        tagService.submitTagAudit(id);
        return Result.success();
    }
}
