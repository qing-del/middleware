package com.jacolp.controller.user;

import com.jacolp.pojo.dto.tag.UserTagAddDTO;
import com.jacolp.pojo.dto.tag.UserTagAssignDTO;
import com.jacolp.pojo.dto.tag.UserTagQueryDTO;
import com.jacolp.pojo.dto.tag.UserTagRemoveDTO;
import com.jacolp.pojo.vo.tag.UserTagSimpleVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TagService;
import com.jacolp.service.UserTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端标签控制器
 */
@RestController("User-TagController")
@RequestMapping("/user/tag")
@Slf4j
@Schema(description = "User - 标签管理")
@Tag(name = "User-标签管理", description = "用户端标签条件查询与审核申请接口")
public class TagController {

    @Autowired
    private TagService tagService;

    @Autowired
    private UserTagService userTagService;

    /**
     * 条件查询标签
     * <p>查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。</p>
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询标签",
            description = "查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(@RequestBody UserTagQueryDTO dto) {
        log.info("User list tags, keyword: {}", dto.getKeyword());
        return Result.success(tagService.listUserTags(dto));
    }

    /**
     * 发起标签审核申请
     * <p>传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。</p>
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起标签审核申请",
            description = "传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit tag audit, tagId: {}", id);
        tagService.submitTagAudit(id);
        return Result.success();
    }

    // ==================== 以下为新增的用户端标签管理接口 ====================

    /**
     * 查询当前用户的标签列表
     * <p>查询当前用户创建的所有标签</p>
     */
    @GetMapping
    @Operation(summary = "查询标签列表",
            description = "查询当前用户创建的所有标签")
    public Result<List<UserTagSimpleVO>> listMyTags() {
        log.info("User query tag list");
        return Result.success(userTagService.listTags());
    }

    /**
     * 创建标签
     * <p>标签所有者自动设为当前登录用户</p>
     */
    @PostMapping
    @Operation(summary = "创建标签",
            description = "创建新标签，所有者自动设为当前登录用户")
    public Result<String> create(@RequestBody UserTagAddDTO dto) {
        log.info("User create tag: {}", dto.getTagName());
        userTagService.createTag(dto);
        return Result.success("创建成功");
    }

    /**
     * 删除标签
     * <p>执行软删除，将 is_deleted 置为 1</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签",
            description = "软删除标签，保留关联历史")
    public Result<String> delete(@PathVariable Long id) {
        log.info("User delete tag: {}", id);
        userTagService.deleteTag(id);
        return Result.success("删除成功");
    }

    /**
     * 绑定标签
     * <p>将标签绑定到笔记或主题</p>
     */
    @PostMapping("/assign")
    @Operation(summary = "绑定标签",
            description = "将标签绑定到笔记或主题")
    public Result<String> assign(@RequestBody UserTagAssignDTO dto) {
        log.info("User assign tag {} to {} {}", dto.getTagId(), dto.getTargetType(), dto.getTargetId());
        userTagService.assignTag(dto);
        return Result.success("绑定成功");
    }

    /**
     * 解除绑定
     * <p>解除标签与笔记或主题之间的绑定</p>
     */
    @PostMapping("/remove")
    @Operation(summary = "解除绑定",
            description = "解除标签与笔记或主题之间的绑定")
    public Result<String> remove(@RequestBody UserTagRemoveDTO dto) {
        log.info("User remove tag {} from {} {}", dto.getTagId(), dto.getTargetType(), dto.getTargetId());
        userTagService.removeTag(dto);
        return Result.success("解除绑定成功");
    }
}