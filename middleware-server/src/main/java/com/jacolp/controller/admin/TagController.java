package com.jacolp.controller.admin;

import com.jacolp.pojo.dto.TagAddDTO;
import com.jacolp.pojo.dto.TagBatchAddDTO;
import com.jacolp.pojo.dto.TagModifyDTO;
import com.jacolp.pojo.dto.TagQueryDTO;
import com.jacolp.pojo.vo.TagBatchAddVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TagService;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class TagController {

    @Autowired
    private TagService tagService;

    @PostMapping("/add")
    @Operation(description = "新增标签")
    public Result<String> add(@RequestBody TagAddDTO dto) {
        log.info("Admin add tag, tagName: {}", dto.getTagName());
        tagService.addTag(dto);
        return Result.success();
    }

    @PostMapping("/batch-add")
    @Operation(description = "批量添加标签")
    public Result<TagBatchAddVO> batchAdd(@RequestBody TagBatchAddDTO dto) {
        return Result.success(tagService.batchAddTags(dto));
    }

    /**
     * 修改标签
     * 不推荐使用这个接口
     */
    @PutMapping("/modify")
    @Operation(description = "修改标签（不推荐）（建议使用添加标签接口）")
    public Result<String> modify(@RequestBody TagModifyDTO dto) {
        log.info("Admin modify tag, id: {}", dto.getId());
        tagService.modifyTag(dto);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(description = "批量删除标签")
    public Result<String> delete(@Parameter(description = "标签ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "标签");
        log.info("Admin delete tags, ids: {}", idList);
        tagService.deleteTags(idList);
        return Result.success();
    }

    @PostMapping("/list")
    @Operation(description = "分页查询标签（前缀模糊）")
    public Result<PageResult> list(@RequestBody TagQueryDTO dto) {
        return Result.success(tagService.listTags(dto));
    }
}
