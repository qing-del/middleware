package com.jacolp.controller.admin;

import com.jacolp.pojo.dto.TopicAddDTO;
import com.jacolp.pojo.dto.TopicListDTO;
import com.jacolp.pojo.dto.TopicModifyDTO;
import com.jacolp.pojo.vo.TopicDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TopicService;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;

@RestController("Admin-TopicController")
@RequestMapping("/admin/topic")
@Slf4j
@Schema(description = "Admin - 主题管理")
@Tag(name = "Admin-主题管理", description = "主题新增、修改、详情、分页和删除接口")
/**
 * Admin 端主题控制器。
 *
 * 说明：
 * - 本控制器仅负责参数接收与返回封装。
 * - 核心业务规则统一下沉到 Service 层。
 */
public class TopicController {

    @Autowired
    private TopicService topicService;

    @PostMapping("/add")
    @Operation(summary = "新增主题", description = "从当前登录用户上下文获取 userId 后创建主题；服务层会先清洗主题名、校验长度，再检查同一用户下主题名称唯一性。")
    public Result<String> add(@RequestBody TopicAddDTO dto) {
        log.info("Admin add topic, topicName: {}", dto.getTopicName());
        topicService.addTopic(dto);
        return Result.success();
    }

    @PutMapping("/modify")
    @Operation(summary = "修改主题", description = "修改主题名称和排序前会先校验目标主题是否存在；若修改名称，还会再次检查同一用户下是否已存在同名主题。")
    public Result<String> modify(@RequestBody TopicModifyDTO dto) {
        log.info("Admin modify topic, id: {}", dto.getId());
        topicService.modifyTopic(dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询主题详情", description = "根据主题 ID 读取单条主题信息，用于编辑页回显或详情展示；若主题不存在则按业务规则返回不存在。")
    public Result<TopicDetailVO> getById(@PathVariable Long id) {
        log.info("Admin get topic by id: {}", id);
        return Result.success(topicService.getTopicById(id));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询主题", description = "按当前用户或指定用户、关键字和分页参数查询主题列表，返回结果按服务层默认排序规则展示。")
    public Result<PageResult> list(@RequestBody TopicListDTO dto) {
        return Result.success(topicService.listTopics(dto));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除主题", description = "批量删除前会先验证所有主题是否存在，并检查每个主题下是否存在未删除笔记；只要存在引用，整批删除即拒绝。")
    public Result<String> delete(@Parameter(description = "主题ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        // 支持前端以字符串形式传入批量 ID："1,2,3"
        List<Long> idList = IdParserUtil.parseIds(ids, "主题");
        log.info("Admin delete topics, ids: {}", idList);
        topicService.deleteTopics(idList);
        return Result.success();
    }
}
