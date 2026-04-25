package com.jacolp.controller.user;

import com.jacolp.context.BaseContext;
import com.jacolp.pojo.dto.note.UserNoteDetailDTO;
import com.jacolp.pojo.dto.note.UserNoteQueryDTO;
import com.jacolp.pojo.dto.note.UserNoteSearchDTO;
import com.jacolp.pojo.dto.note.UserNoteUpdateDTO;
import com.jacolp.pojo.vo.note.UserNoteDetailVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.NoteService;
import com.jacolp.service.UserNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端笔记控制器
 */
@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@Schema(description = "User - 笔记管理")
@Tag(name = "User-笔记管理", description = "用户端笔记条件查询与审核申请接口")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserNoteService userNoteService;

    /**
     * 条件查询笔记
     * <p>查询当前用户自己的笔记 + 别人已发布的笔记。支持按主题 ID、标题筛选，分页返回。</p>
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询笔记",
            description = "查询当前用户自己的笔记 + 别人已发布的笔记。支持按主题 ID、标题筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserNoteQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list notes, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(noteService.listUserNotes(userId, dto));
    }

    /**
     * 发起笔记审核申请
     * <p>传入笔记 ID，发起对该笔记的审核申请。仅允许申请审核自己的笔记，且该笔记不能已通过审核或已有待审核申请。</p>
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起笔记审核申请",
            description = "传入笔记 ID，发起对该笔记的审核申请。仅允许申请审核自己的笔记，且该笔记不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit note audit, noteId: {}", id);
        noteService.submitNoteAudit(id);
        return Result.success();
    }

    // ==================== 以下为新增的用户端笔记管理接口 ====================

    /**
     * 创建笔记
     * <p>通过上传 .md 文件创建笔记内容</p>
     */
    @PostMapping
    @Operation(summary = "创建笔记",
            description = "通过上传 .md 文件创建笔记内容")
    public Result<Long> create(
            @Parameter(description = ".md 文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "主题ID（可选）")
            @RequestParam(value = "topicId", required = false) Long topicId) {
        log.info("User create note: {}, topicId: {}", file.getOriginalFilename(), topicId);
        Long noteId = userNoteService.createNote(file, topicId);
        return Result.success(noteId);
    }

    /**
     * 查询笔记列表
     * <p>仅查询当前用户的笔记，支持按主题、标签等条件筛选</p>
     */
    @GetMapping
    @Operation(summary = "查询笔记列表",
            description = "仅查询当前用户的笔记，支持按主题、标签等条件筛选")
    public Result<PageResult> listMyNotes(UserNoteSearchDTO dto) {
        log.info("User list notes, topicId: {}, keyword: {}", dto.getTopicId(), dto.getKeyword());
        return Result.success(userNoteService.listNotes(dto));
    }

    /**
     * 查看笔记详情
     * <p>根据笔记ID查询详情，返回笔记原文和渲染后的HTML</p>
     */
    @GetMapping("/{id}")
    @Operation(summary = "查看笔记详情",
            description = "根据笔记ID查询详情，返回笔记原文和渲染后的HTML")
    public Result<UserNoteDetailVO> getDetail(@PathVariable Long id) {
        log.info("User get note detail: {}", id);
        UserNoteDetailDTO dto = new UserNoteDetailDTO();
        dto.setId(id);
        UserNoteDetailVO result = userNoteService.getNoteDetail(dto);
        return Result.success(result);
    }

    /**
     * 更新笔记内容
     * <p>用新上传的 Markdown 内容替换原有内容，更新解析结果和渲染缓存</p>
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新笔记内容",
            description = "用新上传的 Markdown 内容替换原有内容")
    public Result<String> update(
            @PathVariable Long id,
            @Parameter(description = ".md 文件")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody UserNoteUpdateDTO dto) {
        log.info("User update note: {}", id);
        dto.setId(id);
        userNoteService.updateNote(file, dto);
        return Result.success("更新成功");
    }

    /**
     * 删除笔记
     * <p>执行软删除，同步更新相关缓存或关联数据</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记",
            description = "执行软删除，同步更新相关缓存或关联数据")
    public Result<String> delete(@PathVariable Long id) {
        log.info("User delete note: {}", id);
        userNoteService.deleteNote(id);
        return Result.success("删除成功");
    }

    /**
     * 全文搜索
     * <p>仅搜索当前用户的笔记，基于标题和内容做关键词检索</p>
     */
    @GetMapping("/search")
    @Operation(summary = "全文搜索",
            description = "仅搜索当前用户的笔记，基于标题和内容做关键词检索")
    public Result<PageResult> search(UserNoteSearchDTO dto) {
        log.info("User search notes, keyword: {}", dto.getKeyword());
        return Result.success(userNoteService.searchNotes(dto));
    }
}