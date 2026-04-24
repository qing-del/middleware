package com.jacolp.controller.admin;

import com.jacolp.annotation.CheckAndUpdateUserStorage;
import com.jacolp.annotation.NoteFileLimit;
import com.jacolp.enums.StorageOperationType;
import com.jacolp.pojo.dto.note.NoteChangeConfirmDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.NoteModifyInfoDTO;
import com.jacolp.pojo.dto.note.NoteQueryDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.vo.ImageSimpleVO;
import com.jacolp.pojo.vo.NoteChangeDiffVO;
import com.jacolp.pojo.vo.NoteConvertResultVO;
import com.jacolp.pojo.vo.NoteDetailVO;
import com.jacolp.pojo.vo.NoteDiffVO;
import com.jacolp.pojo.vo.NoteModifyDiffDetailVO;
import com.jacolp.pojo.vo.NoteRelationDetailVO;
import com.jacolp.pojo.vo.NoteUploadVO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.NoteService;
import com.jacolp.utils.IdParserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;

@RestController("Admin-NoteController")
@RequestMapping("/admin/note")
@Slf4j
@Schema(description = "Admin - 笔记管理")
@Tag(name = "Admin-笔记管理", description = "笔记生命周期管理接口")
public class NoteController {

    @Autowired private NoteService noteService;

    @PostMapping("/upload")
    @NoteFileLimit
    @CheckAndUpdateUserStorage(operationType = StorageOperationType.UPLOAD)
    @Operation(summary = "上传笔记",
            description = "从当前登录用户上下文获取 userId 后上传 Markdown 文件，先校验主题是否存在与同主题同名唯一性，再一次性扫描标签、图片和双链引用并建立映射；同时落库笔记原文、初始化缺失状态，最终返回 noteId 与缺失图片列表。")
    public Result<NoteUploadVO> upload(
            @RequestParam(required = false) Long topicId,
            @Parameter(description = "笔记文件") @RequestParam MultipartFile file) {
        log.info("Admin upload note, topicId: {}, filename: {}", topicId, file.getOriginalFilename());
        return Result.success(noteService.uploadNote(file, topicId));
    }

    @PutMapping("/upload/{noteId}")
    @NoteFileLimit
    @CheckAndUpdateUserStorage(operationType = StorageOperationType.UPLOAD)
    @Operation(summary = "修改笔记源文件",
            description = "校验笔记归属后读取旧 Markdown 内容，与新文件一起重新扫描标签、图片和双链引用并计算 Diff；新内容仅写入临时版本和变更记录，等待后续确认或回滚。")
    public Result<NoteDiffVO> modifySource(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @Parameter(description = "新笔记文件") @RequestParam MultipartFile file) {
        log.info("Admin modify note source, noteId: {}, filename: {}", noteId, file.getOriginalFilename());
        return Result.success(noteService.modifyNoteSource(noteId, file));
    }

    @PostMapping("/upload/{noteId}/confirm")
    @Operation(summary = "确认或取消笔记变更",
            description = "对 modify-upload 产生的待确认 Diff 进行最终处理：确认时用新内容覆盖旧内容并重建关联映射，取消时清理临时内容和变更记录；整个过程保持笔记原有发布状态不变。")
    public Result<NoteChangeDiffVO> confirmChange(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @RequestBody NoteChangeConfirmDTO dto) {
        log.info("Admin confirm note change, noteId: {}", noteId);
        return Result.success(noteService.confirmChange(noteId, dto));
    }

    @GetMapping("/upload/{noteId}/diff")
    @Operation(summary = "查询变更 Diff 详情",
            description = "读取指定笔记的旧内容、新内容和 diff 记录，返回给前端用于变更确认页面展示；若没有待确认内容则按业务规则返回不存在。")
    public Result<NoteModifyDiffDetailVO> getModifyDiff(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note modify diff, noteId: {}", noteId);
        return Result.success(noteService.getModifyDiff(noteId));
    }

    @GetMapping(value = "/source/{noteId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取笔记 Markdown 源内容",
            description = "直接读取数据库中的笔记原文并以纯文本形式返回，供编辑器回显或二次编辑。")
    public String getSource(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note source, noteId: {}", noteId);
        return noteService.adminGetSource(noteId);
    }

    @PostMapping("/convert/{noteId}")
    @Operation(summary = "转换笔记为 HTML",
            description = "转换前先校验笔记不存在缺失关联信息，然后调用 MarkdownHtmlEngine 生成前置元信息、TOC 和正文 HTML，并将结果写入转换缓存表，供前端阅读页直接渲染。")
    public Result<NoteConvertResultVO> convert(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin convert note, noteId: {}", noteId);
        return Result.success(noteService.adminConvertNote(noteId));
    }

    @DeleteMapping("/convert/{noteId}")
    @Operation(summary = "删除笔记转换缓存",
            description = "删除指定笔记的转换缓存记录，同时将发布状态重置为未发布，避免前端继续读取失效内容。")
    public Result<String> deleteConverted(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin delete note converted, noteId: {}", noteId);
        noteService.adminDeleteConverted(noteId);
        return Result.success();
    }

    @PutMapping("/publish/{noteId}/{status}")
    @Operation(summary = "设置笔记发布状态",
            description = "根据 status 设置笔记发布或下架；发布时必须已存在转换缓存，并且标签、图片、双链三类关联都已通过审核，否则拒绝发布。")
    public Result<String> setPublishStatus(
            @Parameter(description = "笔记ID") @PathVariable Long noteId,
            @Parameter(description = "发布状态（1:发布, 0:下架）") @PathVariable Short status) {
        log.info("Admin set note publish status, noteId: {}, status: {}", noteId, status);
        noteService.setNotePublishStatus(noteId, status);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "批量删除笔记", description = "批量删除笔记主记录并同步清理转换结果、Diff、内容和三类关联映射，随后回收当前用户已占用的存储空间。")
    public Result<String> delete(@Parameter(description = "笔记ID，使用英文逗号分隔") @RequestParam String ids) {
        List<Long> idList = IdParserUtil.parseIds(ids, "笔记");
        log.info("Admin delete notes, ids: {}", idList);
        noteService.adminDeleteNotes(idList);
        return Result.success();
    }

    @GetMapping("/images/{noteId}")
    @Operation(summary = "查询笔记关联图片", description = "按笔记 ID 读取图片映射及图片基础信息，返回给前端用于绑定状态展示、差异确认和详情页渲染。")
    public Result<List<ImageSimpleVO>> listImages(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin list note images, noteId: {}", noteId);
        return Result.success(noteService.listImagesByNoteId(noteId));
    }

    @PutMapping("/info")
    @Operation(summary = "修改笔记元信息", description = "修改笔记标题、描述和主题等基础元数据，不变更 Markdown 正文；修改前会校验目标主题有效性和同主题同名唯一性。")
    public Result<String> modifyInfo(@RequestBody NoteModifyInfoDTO dto) {
        log.info("Admin modify note info, noteId: {}", dto.getId());
        noteService.modifyInfo(dto);
        return Result.success();
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询笔记", description = "按用户、主题、标题、发布状态、审核状态和缺失状态等条件分页查询笔记列表，并按创建时间倒序返回。")
    public Result<PageResult> list(@RequestBody NoteQueryDTO dto) {
        log.info("Admin list notes, dto:{}", dto);
        return Result.success(noteService.listNotes(dto));
    }

    @GetMapping("/info/{noteId}")
    @Operation(summary = "查询笔记详情", description = "返回笔记基础元数据，并聚合标签、图片、双链映射及已转换内容，供前端详情页一次性加载。")
    public Result<NoteDetailVO> info(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note info, noteId: {}", noteId);
        return Result.success(noteService.getInfo(noteId));
    }

    @GetMapping("/open/{noteId}")
    @Operation(summary = "打开笔记内容", description = "读取指定笔记的已转换结果（TOC + 正文 HTML），仅在笔记已发布且已转换的情况下返回，用于前端阅读页渲染。")
    public Result<NoteConvertResultVO> open(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin open note, noteId: {}", noteId);
        return Result.success(noteService.adminOpenNote(noteId));
    }

    @GetMapping("/relation/{noteId}")
    @Operation(summary = "查询笔记关联映射", description = "查询笔记与标签、图片、双链笔记三类关联的全部映射行、绑定状态和缺失标记，用于编辑器联动展示。")
    public Result<NoteRelationDetailVO> relationInfo(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note relation info, noteId: {}", noteId);
        return Result.success(noteService.getRelationInfo(noteId));
    }

    @PutMapping("/relation/tag/bind")
    @Operation(summary = "绑定标签映射", description = "为指定标签映射行绑定目标标签，绑定前会校验名称一致性与标签审核状态，成功后刷新笔记缺失信息。")
    public Result<String> bindTag(@RequestBody TagMappingBindDTO dto) {
        log.info("Admin bind tag mapping, mappingId: {}, tagId: {}", dto.getMappingId(), dto.getTagId());
        noteService.bindTagMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/relation/tag/unbind/{mappingId}")
    @Operation(summary = "解绑标签映射", description = "解除指定标签映射行与标签的绑定关系，清空 tagId 与审核标记后重新计算笔记缺失信息。")
    public Result<String> unbindTag(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind tag mapping, mappingId: {}", mappingId);
        noteService.unbindTagMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/relation/image/bind")
    @Operation(summary = "绑定图片映射", description = "为指定图片映射行绑定目标图片，绑定前会校验文件名一致性和图片审核状态，同时计算是否跨用户引用。")
    public Result<String> bindImage(@RequestBody ImageMappingBindDTO dto) {
        log.info("Admin bind image mapping, mappingId: {}, imageId: {}", dto.getMappingId(), dto.getImageId());
        noteService.bindImageMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/relation/image/unbind/{mappingId}")
    @Operation(summary = "解绑图片映射", description = "解除指定图片映射行的图片绑定，清空 imageId 和跨用户标记，并重新判断笔记是否仍存在缺失信息。")
    public Result<String> unbindImage(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind image mapping, mappingId: {}", mappingId);
        noteService.unbindImageMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/relation/each/bind")
    @Operation(summary = "绑定双链笔记映射", description = "为指定双链映射行绑定目标笔记，绑定前会校验标题匹配、目标笔记审核状态和删除状态，成功后刷新缺失信息。")
    public Result<String> bindEach(@RequestBody EachMappingBindDTO dto) {
        log.info("Admin bind note-each mapping, mappingId: {}, noteId: {}", dto.getMappingId(), dto.getNoteId());
        noteService.bindEachMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/relation/each/unbind/{mappingId}")
    @Operation(summary = "解绑双链笔记映射", description = "解除指定双链映射行与目标笔记的绑定关系，清空 targetNoteId 后重算笔记关联完整性。")
    public Result<String> unbindEach(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind note-each mapping, mappingId: {}", mappingId);
        noteService.unbindEachMapping(mappingId);
        return Result.success();
    }

    @PostMapping("/relation/check/{noteId}")
    @Operation(summary = "校验关联完整性", description = "遍历笔记的标签、图片和双链三类映射，判断是否都已完整绑定且审核通过，并将结果回写到 isMissingInfo 字段。")
    public Result<Boolean> checkRelationCompletion(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin check note relation completion, noteId: {}", noteId);
        return Result.success(noteService.checkRelationCompletion(noteId));
    }
}
