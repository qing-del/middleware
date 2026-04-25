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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端标签控制器
 * <p>提供用户端的标签查询、创建、删除、绑定等功能接口</p>
 */
@RestController("User-TagController")
@RequestMapping("/user/tag")
@Slf4j
@Tag(name = "User-标签管理", description = "用户端标签管理接口")
public class TagController {

    @Autowired
    private TagService tagService;

    @Autowired
    private UserTagService userTagService;

    /**
     * 条件查询标签列表
     * <p>查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。</p>
     *
     * @param dto 查询条件，包含关键字、分页参数等
     * @return 分页后的标签列表
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询标签列表", description = "查询当前用户自己的标签 + 别人已通过审核的标签。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(@RequestBody UserTagQueryDTO dto) {
        log.info("User list tags, keyword: {}", dto.getKeyword());
        return Result.success(tagService.listUserTags(dto));
    }

    /**
     * 发起标签审核申请
     * <p>传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。</p>
     *
     * @param id 标签ID
     * @return 审核申请提交结果
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起标签审核申请", description = "传入标签 ID，发起对该标签的审核申请。仅允许申请审核自己的标签，且该标签不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit tag audit, tagId: {}", id);
        tagService.submitTagAudit(id);
        return Result.success("审核申请已提交");
    }

    /**
     * 查询当前用户的标签列表
     * <p>查询当前登录用户创建的所有标签，返回标签的基本信息。</p>
     *
     * @return 当前用户的所有标签列表
     */
    @GetMapping
    @Operation(summary = "查询当前用户标签列表", description = "查询当前登录用户创建的所有标签，返回标签的基本信息（ID、名称、创建时间）。")
    public Result<List<UserTagSimpleVO>> listMyTags() {
        log.info("User query tag list");
        return Result.success(userTagService.listTags());
    }

    /**
     * 创建新标签
     * <p>创建新标签，标签所有者自动设为当前登录用户。标签名称在同一用户下不能重复。</p>
     *
     * @param dto 创建标签请求，包含标签名称
     * @return 创建结果提示
     */
    @PostMapping
    @Operation(summary = "创建标签", description = "创建新标签，标签所有者自动设为当前登录用户。标签名称在同一用户下不能重复。")
    public Result<String> create(@RequestBody UserTagAddDTO dto) {
        log.info("User create tag: {}", dto.getTagName());
        userTagService.createTag(dto);
        return Result.success("创建成功");
    }

    /**
     * 删除标签
     * <p>根据标签ID删除标签。执行软删除，保留关联历史。仅允许删除自己的标签。</p>
     *
     * @param id 标签ID
     * @return 删除结果提示
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签", description = "根据标签ID删除标签。执行软删除，保留关联历史。仅允许删除自己的标签。")
    public Result<String> delete(@PathVariable Long id) {
        log.info("User delete tag: {}", id);
        userTagService.deleteTag(id);
        return Result.success("删除成功");
    }

    /**
     * 绑定标签到资源
     * <p>将标签绑定到笔记或主题。绑定前会校验标签归属和目标资源的存在性。targetType可选值：note（绑定到笔记）、topic（绑定到主题）</p>
     *
     * @param dto 绑定请求，包含标签ID、目标资源ID、目标资源类型
     * @return 绑定结果提示
     */
    @PostMapping("/assign")
    @Operation(summary = "绑定标签到资源", description = "将标签绑定到笔记或主题。绑定前会校验标签归属和目标资源的存在性。targetType可选值：note（绑定到笔记）、topic（绑定到主题）")
    public Result<String> assign(@RequestBody UserTagAssignDTO dto) {
        log.info("User assign tag {} to {} {}", dto.getTagId(), dto.getTargetType(), dto.getTargetId());
        userTagService.assignTag(dto);
        return Result.success("绑定成功");
    }

    /**
     * 解除标签绑定
     * <p>解除标签与笔记或主题之间的绑定关系。仅允许操作自己的标签和资源。targetType可选值：note（从笔记解除）、topic（从主题解除）</p>
     *
     * @param dto 解除绑定请求，包含标签ID、目标资源ID、目标资源类型
     * @return 解除绑定结果提示
     */
    @PostMapping("/remove")
    @Operation(summary = "解除标签绑定", description = "解除标签与笔记或主题之间的绑定关系。仅允许操作自己的标签和资源。targetType可选值：note（从笔记解除）、topic（从主题解除）")
    public Result<String> remove(@RequestBody UserTagRemoveDTO dto) {
        log.info("User remove tag {} from {} {}", dto.getTagId(), dto.getTargetType(), dto.getTargetId());
        userTagService.removeTag(dto);
        return Result.success("解除绑定成功");
    }
}