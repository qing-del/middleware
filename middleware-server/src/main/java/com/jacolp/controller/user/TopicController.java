package com.jacolp.controller.user;

import com.jacolp.pojo.dto.UserTopicQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TopicService;
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

@RestController("User-TopicController")
@RequestMapping("/user/topic")
@Slf4j
@Schema(description = "User - 主题管理")
@Tag(name = "User-主题管理", description = "用户端主题条件查询与审核申请接口")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @PostMapping("/list")
    @Operation(summary = "条件查询主题",
            description = "查询当前用户自己的主题 + 别人已通过审核的主题。支持按关键字模糊搜索，分页返回。")
    public Result<PageResult> list(@RequestBody UserTopicQueryDTO dto) {
        log.info("User list topics, keyword: {}", dto.getKeyword());
        return Result.success(topicService.listUserTopics(dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起主题审核申请",
            description = "传入主题 ID，发起对该主题的审核申请。仅允许申请审核自己的主题，且该主题不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit topic audit, topicId: {}", id);
        topicService.submitTopicAudit(id);
        return Result.success();
    }
}
