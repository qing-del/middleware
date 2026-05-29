package com.jacolp.controller.user;

import com.jacolp.pojo.dto.topic.TopicAddDTO;
import com.jacolp.pojo.dto.topic.TopicModifyDTO;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.jacolp.pojo.dto.topic.UserTopicQueryDTO;
import com.jacolp.pojo.vo.topic.TopicStatsVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TopicService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@RestController("User-TopicController")
@RequestMapping("/user/topic")
@Slf4j
@CrossOrigin("*")
@Schema(description = "User - 主题管理")
@Tag(name = "User-主题管理", description = "用户端主题条件查询与审核申请接口")
public class TopicController {

    @Autowired private TopicService topicService;

    @PostMapping("/list")
    @Operation(summary = "条件查询主题",
            description = "查询当前用户自己的主题 + 别人已通过审核的主题。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(
            @Parameter(description = "主题查询条件（关键词、分页参数）") @RequestBody UserTopicQueryDTO dto) {
        log.info("User list topics, keyword: {}", dto.getKeyword());
        return Result.success(topicService.listUserTopics(dto));
    }

    @GetMapping("/stats")
    @Operation(summary = "获取用户主题统计",
            description = "返回当前用户的主题总数和已通过审核数。")
    public Result<TopicStatsVO> getStats() {
        log.info("User get topic stats");
        return Result.success(topicService.getUserTopicStats());
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起主题审核申请",
            description = "传入主题 ID，发起对该主题的审核申请。仅允许申请审核自己的主题，且该主题不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@Parameter(description = "主题ID") @RequestParam Long id) {
        log.info("User submit topic audit, topicId: {}", id);
        topicService.submitTopicAudit(id);
        return Result.success();
    }

    @PostMapping("/cancelAudit")
    @Operation(summary = "撤销主题审核申请",
            description = "撤销当前用户的主题审核申请，仅删除待审核记录。")
    public Result<String> cancelAudit(@Parameter(description = "主题ID") @RequestParam Long id) {
        log.info("User cancel topic audit, topicId: {}", id);
        topicService.cancelTopicAudit(id);
        return Result.success("审核申请已撤销");
    }

    @PostMapping("/add")
    @Operation(summary = "新增主题",
            description = "从当前登录用户上下文获取 userId 后创建主题；服务层会先清洗主题名、校验长度，再检查同一用户下主题名称唯一性。")
    public Result<String> add(
            @Parameter(description = "新增主题请求（主题名称）") @RequestBody TopicAddDTO dto) {
        log.info("User add topic, topicName: {}", dto.getTopicName());
        topicService.addTopic(dto);
        return Result.success();
    }

    @PutMapping("/modify")
    @Operation(summary = "修改主题", description = "修改主题的排序等级。")
    public Result<String> modify(
            @Parameter(description = "修改主题请求（主题ID、排序等级）") @RequestBody TopicModifyDTO dto) {
        log.info("User modify topic, id: {}", dto.getId());
        topicService.modifyTopic(dto);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除主题",
            description = "批量删除前会先验证所有主题是否存在，并检查每个主题下是否存在未删除笔记；只要存在引用，整批删除即拒绝。")
    public Result<String> delete(@Parameter(description = "主题ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        // 支持前端以字符串形式传入批量 ID："1,2,3"
        List<Long> idList = IdParserUtil.parseIds(ids, "主题");
        log.info("User delete topics, ids: {}", idList);
        topicService.deleteTopics(idList);
        return Result.success();
    }
}
