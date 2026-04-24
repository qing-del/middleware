package com.jacolp.controller.user;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.note.UserNoteQueryDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.NoteService;
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

@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@Schema(description = "User - 笔记管理")
@Tag(name = "User-笔记管理", description = "用户端笔记条件查询与审核申请接口")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @PostMapping("/list")
    @Operation(summary = "条件查询笔记",
            description = "查询当前用户自己的笔记 + 别人已发布的笔记。支持按主题 ID、标题筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserNoteQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list notes, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(noteService.listUserNotes(userId, dto));
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起笔记审核申请",
            description = "传入笔记 ID，发起对该笔记的审核申请。仅允许申请审核自己的笔记，且该笔记不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit note audit, noteId: {}", id);
        noteService.submitNoteAudit(id);
        return Result.success();
    }
}
