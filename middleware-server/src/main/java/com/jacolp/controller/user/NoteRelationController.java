package com.jacolp.controller.user;

import com.jacolp.facade.NoteFacade;
import com.jacolp.pojo.dto.image.ImageMappingBindDTO;
import com.jacolp.pojo.dto.note.EachMappingBindDTO;
import com.jacolp.pojo.dto.tag.TagMappingBindDTO;
import com.jacolp.pojo.vo.image.ImageSimpleVO;
import com.jacolp.pojo.vo.note.NoteCheckBindingVO;
import com.jacolp.pojo.vo.note.NoteRelationDetailVO;
import com.jacolp.result.Result;
import com.jacolp.service.NoteRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("User-NoteRelationController")
@RequestMapping("/user/note/relation")
@Schema(description = "User - 笔记关联管理")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User-笔记关联管理", description = "用户端笔记关联管理接口")
@Slf4j
@CrossOrigin("*")
public class NoteRelationController {

    @Autowired private NoteFacade noteFacade;
    @Autowired private NoteRelationService noteRelationService;

    @PostMapping("/check/{noteId}")
    @Operation(summary = "校验关联完整性",
            description = "遍历笔记的标签、图片和双链三类映射，判断是否都已完整绑定且审核通过，会自动转换笔记状态；如果收到的结果中`isCompeted`这个值不为true即为缺失信息转换失败。")
    public Result<NoteCheckBindingVO> checkRelationCompletion(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin check note relation completion, noteId: {}", noteId);
        return Result.success(noteFacade.checkRelationCompletion(noteId));
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "查询笔记关联映射",
            description = "查询笔记与标签、图片、双链笔记三类关联的全部映射行、绑定状态和缺失标记，用于编辑器联动展示。")
    public Result<NoteRelationDetailVO> relationInfo(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin get note relation info, noteId: {}", noteId);
        return Result.success(noteFacade.getRelationInfo(noteId));
    }

    @GetMapping("/images/{noteId}")
    @Operation(summary = "查询笔记关联图片",
            description = "按笔记 ID 读取图片映射及图片基础信息，返回给前端用于绑定状态展示、差异确认和详情页渲染。")
    public Result<List<ImageSimpleVO>> listImages(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin list note images, noteId: {}", noteId);
        return Result.success(noteFacade.listImageSimpleVOsByNoteId(noteId));
    }

    @PutMapping("/tag/bind")
    @Operation(summary = "绑定标签映射",
            description = "为指定标签映射行绑定目标标签，绑定前会校验名称一致性与标签审核状态，成功后刷新笔记缺失信息。")
    public Result<String> bindTag(
            @Parameter(description = "标签绑定请求（映射行ID、目标标签ID）") @RequestBody TagMappingBindDTO dto) {
        log.info("Admin bind tag mapping, mappingId: {}, tagId: {}", dto.getMappingId(), dto.getTagId());
        noteFacade.bindTagMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/tag/unbind/{mappingId}")
    @Operation(summary = "解绑标签映射",
            description = "解除指定标签映射行与标签的绑定关系，清空 tagId 与审核标记后重新计算笔记缺失信息。")
    public Result<String> unbindTag(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind tag mapping, mappingId: {}", mappingId);
        noteRelationService.unbindTagMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/image/bind")
    @Operation(summary = "绑定图片映射",
            description = "为指定图片映射行绑定目标图片，绑定前会校验文件名一致性和图片审核状态，同时计算是否跨用户引用。")
    public Result<String> bindImage(
            @Parameter(description = "图片绑定请求（映射行ID、目标图片ID）") @RequestBody ImageMappingBindDTO dto) {
        log.info("Admin bind image mapping, mappingId: {}, imageId: {}", dto.getMappingId(), dto.getImageId());
        noteFacade.bindImageMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/image/unbind/{mappingId}")
    @Operation(summary = "解绑图片映射",
            description = "解除指定图片映射行的图片绑定，清空 imageId 和跨用户标记，并重新判断笔记是否仍存在缺失信息。")
    public Result<String> unbindImage(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind image mapping, mappingId: {}", mappingId);
        noteRelationService.unbindImageMapping(mappingId);
        return Result.success();
    }

    @PutMapping("/each/bind")
    @Operation(summary = "绑定双链笔记映射",
            description = "为指定双链映射行绑定目标笔记，绑定前会校验标题匹配、目标笔记审核状态和删除状态，成功后刷新缺失信息。")
    public Result<String> bindEach(
            @Parameter(description = "双链绑定请求（映射行ID、目标笔记ID）") @RequestBody EachMappingBindDTO dto) {
        log.info("Admin bind note-each mapping, mappingId: {}, noteId: {}", dto.getMappingId(), dto.getNoteId());
        noteFacade.bindEachMapping(dto);
        return Result.success();
    }

    @DeleteMapping("/each/unbind/{mappingId}")
    @Operation(summary = "解绑双链笔记映射",
            description = "解除指定双链映射行与目标笔记的绑定关系，清空 targetNoteId 后重算笔记关联完整性。")
    public Result<String> unbindEach(@Parameter(description = "映射行ID") @PathVariable Long mappingId) {
        log.info("Admin unbind note-each mapping, mappingId: {}", mappingId);
        noteRelationService.unbindEachMapping(mappingId);
        return Result.success();
    }
}
