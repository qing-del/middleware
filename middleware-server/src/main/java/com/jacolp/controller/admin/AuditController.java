package com.jacolp.controller.admin;

import com.jacolp.facade.AuditFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.jacolp.pojo.dto.audit.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.image.ImageAuditListDTO;
import com.jacolp.pojo.dto.audit.MetaAuditListDTO;
import com.jacolp.pojo.dto.note.NoteAuditListDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.AuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController("Admin-AuditController")
@RequestMapping("/admin/audit")
@Slf4j
@CrossOrigin("*")
@Schema(description = "Admin - 审核管理")
@Tag(name = "Admin-审核管理", description = "标签、图片与笔记的审核记录查询与批量审核接口")
public class AuditController {

    @Autowired private AuditFacade auditFacade;
    @Autowired private AuditService auditService;

    @PostMapping("/meta/list")
    @Operation(summary = "分页查询标签审核记录",
            description = "按审核状态和申请人筛选标签审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listMeta(
            @Parameter(description = "标签审核查询条件（审核状态、申请人）") @RequestBody MetaAuditListDTO dto) {
        log.info("Admin list meta audits, applyType: {}, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getApplyType(),
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listMetaAudits(dto));
    }

    @PostMapping("/image/list")
    @Operation(summary = "分页查询图片审核记录",
            description = "按审核状态和申请人筛选图片审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listImage(
            @Parameter(description = "图片审核查询条件（审核状态、申请人）") @RequestBody ImageAuditListDTO dto) {
        log.info("Admin list image audits, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listImageAudits(dto));
    }

    @PostMapping("/note/list")
    @Operation(summary = "分页查询笔记审核记录",
            description = "按审核状态和申请人筛选笔记审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listNote(
            @Parameter(description = "笔记审核查询条件（审核状态、申请人）") @RequestBody NoteAuditListDTO dto) {
        log.info("Admin list note audits, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listNoteAudits(dto));
    }

    @PutMapping("/meta/review/batch")
    @Operation(summary = "批量审核标签",
            description = "批量审核标签申请，仅处理审核中记录并同步回写 biz_tag 的 audit_status，返回实际处理数量。")
    public Result<Integer> batchReviewMeta(
            @Parameter(description = "批量审核请求（审核记录ID列表及审核结果）") @RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review meta audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditFacade.batchReviewMeta(dto));
    }

    @PutMapping("/image/review/batch")
    @Operation(summary = "批量审核图片",
            description = "批量审核图片申请，仅处理审核中记录并同步回写 biz_image 的 audit_status，返回实际处理数量。")
    public Result<Integer> batchReviewImage(
            @Parameter(description = "批量审核请求（审核记录ID列表及审核结果）") @RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review image audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditFacade.batchReviewImage(dto));
    }

    @PutMapping("/note/review/batch")
    @Operation(summary = "批量审核笔记",
            description = "批量审核笔记申请，仅处理待审核记录并同步回写 biz_note 的 is_pass，返回实际处理数量。")
    public Result<Integer> batchReviewNote(
            @Parameter(description = "批量审核请求（审核记录ID列表及审核结果）") @RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review note audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditFacade.batchReviewNote(dto));
    }
}
