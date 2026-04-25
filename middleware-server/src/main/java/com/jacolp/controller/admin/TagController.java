package com.jacolp.controller.admin;

import com.jacolp.pojo.dto.tag.TagAddDTO;
import com.jacolp.pojo.dto.tag.TagBatchAddDTO;
import com.jacolp.pojo.dto.tag.TagModifyDTO;
import com.jacolp.pojo.dto.tag.TagQueryDTO;
import com.jacolp.pojo.vo.tag.TagBatchAddVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TagService;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("Admin-TagController")
@RequestMapping("/admin/tag")
@Slf4j
@Schema(description = "Admin - 标签管理")
@Tag(name = "Admin-标签管理", description = "标签新增、批量新增、修改、删除与分页查询接口")
public class TagController {

    @Autowired
    private TagService tagService;

    @PostMapping("/add")
    @Operation(summary = "新增标签", description = "从当前登录用户上下文获取 userId 后创建单个标签；服务层会先清洗名称、校验长度，再检查同名标签是否已存在，避免重复创建。")
    public Result<String> add(@RequestBody TagAddDTO dto) {
        log.info("Admin add tag, tagName: {}", dto.getTagName());
        tagService.addTag(dto);
        return Result.success();
    }

    @PostMapping("/batch-add")
    @Operation(summary = "批量新增标签", description = "批量创建标签时先去重并过滤空值，再对比当前用户已有标签列表；仅插入不存在的标签，返回成功数量和已存在标签列表。")
    public Result<TagBatchAddVO> batchAdd(@RequestBody TagBatchAddDTO dto) {
        return Result.success(tagService.batchAddTags(dto));
    }

    /**
     * 修改标签
     * 不推荐使用这个接口
     */
    @PutMapping("/modify")
    @Operation(summary = "修改标签", description = "修改标签名称前会先按 userId 校验标签归属与存在性，并再次检查新名称是否与当前用户已有标签冲突。")
    public Result<String> modify(@RequestBody TagModifyDTO dto) {
        log.info("Admin modify tag, id: {}", dto.getId());
        tagService.modifyTag(dto);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除标签", description = "批量删除前会先检查所有目标标签是否存在，并查询其被笔记引用的数量；只要有任一标签仍被使用，整批删除即拒绝执行。")
    public Result<String> delete(@Parameter(description = "标签ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "标签");
        log.info("Admin delete tags, ids: {}", idList);
        tagService.deleteTags(idList);
        return Result.success();
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询标签", description = "按关键词前缀进行模糊匹配并分页返回标签列表，支持按当前用户或指定用户维度查询。")
    public Result<PageResult> list(@RequestBody TagQueryDTO dto) {
        return Result.success(tagService.listTags(dto));
    }
}
