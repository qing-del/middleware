package com.jacolp.controller.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.facade.NoteRelationFacade;
import com.jacolp.pojo.vo.note.NoteBacklinkVO;
import com.jacolp.result.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("Admin-NoteRelationController")
@RequestMapping("/admin/note/relation")
@Schema(description = "Admin - 笔记关联管理")
@Tag(name = "Admin-笔记关联管理", description = "管理端笔记关联查询接口")
@Slf4j
@CrossOrigin("*")
public class NoteRelationController {

    @Autowired private NoteRelationFacade noteRelationFacade;

    @GetMapping("/backlinks/{noteId}")
    @Operation(summary = "查询反向引用笔记 (Admin)",
            description = "按笔记 ID 反查所有引用了它的源笔记。管理端不过滤所有权与发布状态，返回所有未删除的引用关系。")
    public Result<List<NoteBacklinkVO>> listBacklinks(@Parameter(description = "笔记ID") @PathVariable Long noteId) {
        log.info("Admin list note backlinks, noteId: {}", noteId);
        return Result.success(noteRelationFacade.listBacklinksByNoteId(noteId));
    }
}
