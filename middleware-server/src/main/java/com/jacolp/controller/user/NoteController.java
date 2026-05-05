package com.jacolp.controller.user;

import com.jacolp.annotation.NoteFileLimit;
import com.jacolp.pojo.vo.note.NoteDetailVO;
import com.jacolp.pojo.vo.note.NoteDiffVO;
import com.jacolp.service.NoteConvertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jacolp.facade.NoteFacade;
import com.jacolp.pojo.dto.note.NoteModifyInfoDTO;
import com.jacolp.pojo.dto.note.UserNoteQueryDTO;
import com.jacolp.pojo.dto.note.UserNoteSearchDTO;
import com.jacolp.pojo.dto.note.UserNoteUpdateDTO;
import com.jacolp.pojo.entity.NoteContextEntity;
import com.jacolp.pojo.vo.note.NoteConvertResultVO;
import com.jacolp.pojo.vo.note.NoteStatsVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.NoteContextService;
import com.jacolp.service.NoteCoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户端笔记控制器
 */
@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-笔记管理", description = "用户端笔记管理接口")
public class NoteController {

    @Autowired private NoteFacade noteFacade;
    @Autowired private NoteCoreService noteCoreService;
    @Autowired private NoteContextService noteContextService;
    @Autowired private NoteConvertService noteConvertService;

    @PostMapping("/list")
    @Operation(summary = "条件查询笔记列表")
    public Result<PageResult> list(@RequestBody UserNoteQueryDTO dto) {
        log.info("User list notes, topicId: {}",dto.getTopicId());
        return Result.success(noteCoreService.listUserNotes(dto));
    }

    @GetMapping("/overview")
    @Operation(summary = "获取用户笔记统计")
    public Result<NoteStatsVO> getOverview() {
        log.info("User get note overview");
        return Result.success(noteCoreService.getUserNoteOverview());
    }

    @PostMapping("/submitAudit")
    @Operation(summary = "发起笔记审核申请")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit note audit, noteId: {}", id);
        noteCoreService.submitNoteAudit(id);    // TODO 解耦审核逻辑部分的时候需要优化
        return Result.success("审核申请已提交");
    }

    @PostMapping
    @NoteFileLimit
    @Operation(summary = "创建笔记")
    public Result<Long> create(
            @Parameter(description = ".md 格式的笔记文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "所属主题ID（可选）")
            @RequestParam(value = "topicId", required = false) Long topicId) {
        log.info("User create note: {}, topicId: {}", file.getOriginalFilename(), topicId);
        return Result.success(noteFacade.uploadNote(file, topicId).getNoteId());
    }

    @GetMapping
    @Operation(summary = "查询当前用户笔记列表")
    public Result<PageResult> listMyNotes(UserNoteSearchDTO dto) {
        log.info("User list notes, topicId: {}, keyword: {}", dto.getTopicId(), dto.getKeyword());
        return Result.success(noteCoreService.listUserNotesBySearch(dto));
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "查看笔记详情")
    public Result<NoteDetailVO> getDetail(
            @Parameter(description = "笔记ID")
            @PathVariable Long noteId) {
        log.info("User get note detail: {}", noteId);
        return Result.success(noteFacade.getInfo(noteId));
    }

    @GetMapping(value = "/source/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取笔记Markdown源内容")
    public Result<String> getSource(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User get note source: {}", id);
        NoteContextEntity context = noteContextService.getByNoteIdWithValid(id);
        return Result.success(context != null ? context.getMarkdownContent() : "");
    }

    @GetMapping("/converted/{noteId}")
    @Operation(summary = "获取笔记转换后的HTML内容")
    public Result<NoteConvertResultVO> getConvertedHtml(
            @Parameter(description = "笔记ID")
            @PathVariable Long noteId) {
        log.info("User get note converted HTML: {}", noteId);
        return Result.success(noteConvertService.getNoteConvert(noteId));
    }


    @PostMapping("/convert")
    @Operation(summary = "转换笔记",
        description = "将笔记的 Markdown 源文件转换成 HTML 文件，并保存到数据库中。")
    public Result convert(Long noteId) {
        log.info("User convert note: {}", noteId);
        noteFacade.convertNote(noteId);
        return Result.success();
    }

    @PutMapping("/{id}/info")
    @Operation(summary = "修改笔记元信息")
    public Result updateInfo(
            @PathVariable Long id,
            @RequestBody NoteModifyInfoDTO dto) {
        log.info("User update note info: {}", id);
        noteCoreService.modifyInfo(dto);
        return Result.success();
    }

    @PutMapping("/{id}/source")
    @NoteFileLimit
    @Operation(summary = "修改笔记源文件")
    public Result<NoteDiffVO> updateSource(
            @PathVariable Long id,
            @Parameter(description = "新的 .md 文件")
            @RequestParam("file") MultipartFile file) {
        log.info("User update note source: {}", id);
        return Result.success(noteFacade.modifyNoteSource(id, file));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记")
    public Result delete(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User delete note: {}", id);
        noteFacade.deleteNote(id);
        return Result.success();
    }

    @GetMapping("/search")
    @Operation(summary = "全文搜索笔记")
    public Result<PageResult> search(UserNoteSearchDTO dto) {
        log.info("User search notes, keyword: {}", dto.getKeyword());
        return Result.success(noteCoreService.searchUserNotes(dto));
    }
}
