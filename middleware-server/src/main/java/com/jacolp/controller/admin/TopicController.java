package com.jacolp.controller.admin;

import com.jacolp.exception.BaseException;
import com.jacolp.pojo.dto.TopicAddDTO;
import com.jacolp.pojo.dto.TopicListDTO;
import com.jacolp.pojo.dto.TopicModifyDTO;
import com.jacolp.pojo.vo.TopicDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RestController("Admin-TopicController")
@RequestMapping("/admin/topic")
@Slf4j
@Schema(description = "Admin - 主题管理")
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
    @Operation(description = "新增主题")
    public Result<String> add(@RequestBody TopicAddDTO dto) {
        log.info("Admin add topic, topicName: {}", dto.getTopicName());
        topicService.addTopic(dto);
        return Result.success();
    }

    @PutMapping("/modify")
    @Operation(description = "修改主题")
    public Result<String> modify(@RequestBody TopicModifyDTO dto) {
        log.info("Admin modify topic, id: {}", dto.getId());
        topicService.modifyTopic(dto);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(description = "根据 ID 查询主题详情")
    public Result<TopicDetailVO> getById(@PathVariable Long id) {
        log.info("Admin get topic by id: {}", id);
        return Result.success(topicService.getTopicById(id));
    }

    @PostMapping("/list")
    @Operation(description = "分页查询主题")
    public Result<PageResult> list(@RequestBody TopicListDTO dto) {
        return Result.success(topicService.listTopics(dto));
    }

    @DeleteMapping("/delete")
    @Operation(description = "批量删除主题")
    public Result<String> delete(@Parameter(description = "主题ID，使用英文逗号分隔，例如 1,2,3")
                                 @RequestParam String ids) {
        // 支持前端以字符串形式传入批量 ID："1,2,3"
        List<Long> idList = parseIds(ids);
        log.info("Admin delete topics, ids: {}", idList);
        topicService.deleteTopics(idList);
        return Result.success();
    }

    private List<Long> parseIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            throw new BaseException("待删除的主题 ID 列表不能为空");
        }

        /*
         * LinkedHashSet 作用：
         * 1. 去重，避免重复 ID 干扰删除校验；
         * 2. 保持输入顺序，便于日志排查。
         */
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        String[] parts = ids.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            try {
                idSet.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ex) {
                throw new BaseException("主题 ID 非法: " + trimmed);
            }
        }

        List<Long> result = new ArrayList<>(idSet);
        if (result.isEmpty()) {
            throw new BaseException("待删除的主题 ID 列表不能为空");
        }
        return result;
    }
}