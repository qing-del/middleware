package com.jacolp.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.pojo.dto.UserTopicQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TopicService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-TopicController")
@RequestMapping("/user/topic")
@Slf4j
@Schema(description = "User - 主题管理")
@Tag(name = "User-主题管理", description = "用户端主题查询与主题审核申请接口")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @PostMapping("/list")
    @Operation(summary = "条件查询主题",
            description = "查询范围为“当前用户自己的主题 + 其他用户已通过审核的主题”；支持关键词模糊匹配，并按分页参数返回列表。")
    public Result<PageResult> list(@RequestBody UserTopicQueryDTO dto) {
        log.info("User list topics, keyword: {}", dto.getKeyword());
        return Result.success(topicService.listUserTopics(dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起主题审核申请",
            description = "提交主题审核申请前会校验：主题 ID 合法、主题存在且归属于当前用户、主题尚未通过审核、当前不存在待审核申请。")
    public Result<String> submitAudit(@Parameter(description = "主题 ID") @RequestParam Long id) {
        log.info("User submit topic audit, topicId: {}", id);
        topicService.submitTopicAudit(id);
        return Result.success();
    }
}
