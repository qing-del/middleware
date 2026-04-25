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
 * <p>提供用户端的笔记创建、查询、更新、删除等功能接口</p>
 */
@RestController("User-NoteController")
@RequestMapping("/user/note")
@Slf4j
@Tag(name = "User-笔记管理", description = "用户端笔记管理接口")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserNoteService userNoteService;

    /**
     * 条件查询笔记列表
     * <p>查询当前用户自己的笔记 + 别人已发布的笔记。支持按主题 ID、标题筛选，分页返回。</p>
     *
     * @param dto 查询条件，包含主题ID、标题、分页参数等
     * @return 分页后的笔记列表
     */
    @PostMapping("/list")
    @Operation(summary = "条件查询笔记列表", description = "查询当前用户自己的笔记 + 别人已发布的笔记。支持按主题 ID、标题筛选，分页返回。")
    public Result<PageResult> list(@RequestBody UserNoteQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("User list notes, userId: {}, topicId: {}", userId, dto.getTopicId());
        return Result.success(noteService.listUserNotes(userId, dto));
    }

    /**
     * 发起笔记审核申请
     * <p>传入笔记 ID，发起对该笔记的审核申请。仅允许申请审核自己的笔记，且该笔记不能已通过审核或已有待审核申请。</p>
     *
     * @param id 笔记ID
     * @return 审核申请提交结果
     */
    @PostMapping("/submitAudit")
    @Operation(summary = "发起笔记审核申请", description = "传入笔记 ID，发起对该笔记的审核申请。仅允许申请审核自己的笔记，且该笔记不能已通过审核或已有待审核申请。")
    public Result<String> submitAudit(@RequestParam Long id) {
        log.info("User submit note audit, noteId: {}", id);
        noteService.submitNoteAudit(id);
        return Result.success("审核申请已提交");
    }

    /**
     * 创建笔记
     * <p>通过上传 .md 文件创建笔记内容。会自动解析文件中的标签、图片、内联笔记等关联信息，并建立关联映射。</p>
     *
     * @param file .md 格式的笔记文件
     * @param topicId 所属主题ID（可选），用于分类管理
     * @return 创建成功的笔记ID
     */
    @PostMapping
    @Operation(summary = "创建笔记", description = "通过上传 .md 文件创建笔记内容。会自动解析文件中的标签、图片、内联笔记等关联信息，并建立关联映射。")
    public Result<Long> create(
            @Parameter(description = ".md 格式的笔记文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "所属主题ID（可选）")
            @RequestParam(value = "topicId", required = false) Long topicId) {
        log.info("User create note: {}, topicId: {}", file.getOriginalFilename(), topicId);
        Long noteId = userNoteService.createNote(file, topicId);
        return Result.success(noteId);
    }

    /**
     * 查询当前用户的笔记列表
     * <p>仅查询当前用户的笔记，支持按主题、关键词等条件筛选。</p>
     *
     * @param dto 查询条件，包含主题ID、关键词、分页参数等
     * @return 分页后的笔记列表
     */
    @GetMapping
    @Operation(summary = "查询当前用户笔记列表", description = "仅查询当前用户的笔记，支持按主题、关键词等条件筛选。")
    public Result<PageResult> listMyNotes(UserNoteSearchDTO dto) {
        log.info("User list notes, topicId: {}, keyword: {}", dto.getTopicId(), dto.getKeyword());
        return Result.success(userNoteService.listNotes(dto));
    }

    /**
     * 查看笔记详情
     * <p>根据笔记ID查询详情，返回笔记原文（Markdown）和渲染后的HTML内容，以及标签列表、创建时间等信息。</p>
     *
     * @param id 笔记ID
     * @return 笔记详情，包含原文、渲染后的HTML、标签列表等
     */
    @GetMapping("/{id}")
    @Operation(summary = "查看笔记详情", description = "根据笔记ID查询详情，返回笔记原文（Markdown）和渲染后的HTML内容，以及标签列表、创建时间等信息。")
    public Result<UserNoteDetailVO> getDetail(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User get note detail: {}", id);
        UserNoteDetailDTO dto = new UserNoteDetailDTO();
        dto.setId(id);
        UserNoteDetailVO result = userNoteService.getNoteDetail(dto);
        return Result.success(result);
    }

    /**
     * 更新笔记内容
     * <p>用新上传的 Markdown 内容替换原有内容，自动更新解析结果和渲染缓存。支持仅更新标题、描述、主题等元信息。</p>
     *
     * @param id 笔记ID
     * @param file 新的 .md 文件（可选，不传则仅更新元信息）
     * @param dto 更新请求，包含标题、描述、主题ID等
     * @return 更新结果提示
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新笔记内容", description = "用新上传的 Markdown 内容替换原有内容，自动更新解析结果和渲染缓存。支持仅更新标题、描述、主题等元信息。")
    public Result<String> update(
            @PathVariable Long id,
            @Parameter(description = "新的 .md 文件（可选）")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody UserNoteUpdateDTO dto) {
        log.info("User update note: {}", id);
        dto.setId(id);
        userNoteService.updateNote(file, dto);
        return Result.success("更新成功");
    }

    /**
     * 删除笔记
     * <p>根据笔记ID删除笔记。执行软删除，保留关联历史数据。仅允许删除自己的笔记。</p>
     *
     * @param id 笔记ID
     * @return 删除结果提示
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记", description = "根据笔记ID删除笔记。执行软删除，保留关联历史数据。仅允许删除自己的笔记。")
    public Result<String> delete(
            @Parameter(description = "笔记ID")
            @PathVariable Long id) {
        log.info("User delete note: {}", id);
        userNoteService.deleteNote(id);
        return Result.success("删除成功");
    }

    /**
     * 全文搜索笔记
     * <p>仅搜索当前用户的笔记，基于标题和内容做关键词检索。</p>
     *
     * @param dto 搜索条件，包含关键词、主题ID（可选）、分页参数等
     * @return 分页后的搜索结果
     */
    @GetMapping("/search")
    @Operation(summary = "全文搜索笔记", description = "仅搜索当前用户的笔记，基于标题和内容做关键词检索。")
    public Result<PageResult> search(UserNoteSearchDTO dto) {
        log.info("User search notes, keyword: {}", dto.getKeyword());
        return Result.success(userNoteService.searchNotes(dto));
    }
}