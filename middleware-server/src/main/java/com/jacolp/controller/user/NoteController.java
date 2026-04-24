package com.jacolp.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.UserNoteQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.NoteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@Schema(description = "User - 笔记管理")
@Tag(name = "User-笔记管理", description = "用户端笔记查询与笔记审核申请接口")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @PostMapping("/list")
    @Operation(summary = "条件查询笔记",
            description = "查询范围为“当前用户自己的笔记 + 其他用户已发布的笔记”；支持按主题 ID、标题筛选，并按分页参数返回列表。")
    public Result<PageResult> list(@RequestBody UserNoteQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list notes, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(noteService.listUserNotes(userId, dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起笔记审核申请",
            description = "提交笔记审核申请前会校验：笔记 ID 合法、笔记存在且归属于当前用户、笔记尚未通过审核，且当前不存在待审核申请。")
    public Result<String> submitAudit(@Parameter(description = "笔记 ID") @RequestParam Long id) {
        log.info("User submit note audit, noteId: {}", id);
        noteService.submitNoteAudit(id);
        return Result.success();
    }
}
