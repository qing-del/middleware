package com.jacolp.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jacolp.pojo.dto.AuditBatchReviewDTO;
import com.jacolp.pojo.dto.ImageAuditListDTO;
import com.jacolp.pojo.dto.MetaAuditListDTO;
import com.jacolp.pojo.dto.NoteAuditListDTO;
import com.jacolp.result.PageResult;
import com.jacolp.result.Result;
import com.jacolp.service.AuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/audit")
@Slf4j
@Schema(description = "Admin - 审核管理")
public class AuditController {

    @Autowired private AuditService auditService;

    @PostMapping("/meta/list")
    @Operation(summary = "分页查询主题/标签审核记录",
            description = "按申请类型、审核状态和申请人筛选元数据审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listMeta(@RequestBody MetaAuditListDTO dto) {
        log.info("Admin list meta audits, applyType: {}, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getApplyType(),
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listMetaAudits(dto));
    }

    @PostMapping("/image/list")
    @Operation(summary = "分页查询图片审核记录",
            description = "按审核状态和申请人筛选图片审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listImage(@RequestBody ImageAuditListDTO dto) {
        log.info("Admin list image audits, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listImageAudits(dto));
    }

    @PostMapping("/note/list")
    @Operation(summary = "分页查询笔记审核记录",
            description = "按审核状态和申请人筛选笔记审核记录，按 update_time 倒序返回。")
    public Result<PageResult> listNote(@RequestBody NoteAuditListDTO dto) {
        log.info("Admin list note audits, status: {}, applicantUserId: {}",
                dto == null ? null : dto.getStatus(),
                dto == null ? null : dto.getApplicantUserId());
        return Result.success(auditService.listNoteAudits(dto));
    }

    @PutMapping("/meta/review/batch")
    @Operation(summary = "批量审核主题/标签",
            description = "批量审核元数据申请，仅处理待审核记录并同步回写 biz_topic/biz_tag 的 is_pass，返回实际处理数量。")
    public Result<Integer> batchReviewMeta(@RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review meta audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditService.batchReviewMeta(dto));
    }

    @PutMapping("/image/review/batch")
    @Operation(summary = "批量审核图片",
            description = "批量审核图片申请，仅处理待审核记录并同步回写 biz_image 的 is_pass，返回实际处理数量。")
    public Result<Integer> batchReviewImage(@RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review image audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditService.batchReviewImage(dto));
    }

    @PutMapping("/note/review/batch")
    @Operation(summary = "批量审核笔记",
            description = "批量审核笔记申请，仅处理待审核记录并同步回写 biz_note 的 is_pass，返回实际处理数量。")
    public Result<Integer> batchReviewNote(@RequestBody AuditBatchReviewDTO dto) {
        log.info("Admin batch review note audits, idsSize: {}, status: {}",
                dto == null || dto.getIds() == null ? 0 : dto.getIds().size(),
                dto == null ? null : dto.getStatus());
        return Result.success(auditService.batchReviewNote(dto));
    }
}
