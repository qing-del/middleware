package com.jacolp.document.controller;

import com.jacolp.common.core.result.Result;
import com.jacolp.common.security.context.BaseContext;
import com.jacolp.document.api.model.DocumentMetadata;
import com.jacolp.document.application.metadata.DocumentMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供文档元数据 HTTP 接口；正文 CRDT 状态只通过 WebSocket 协作链路传输。 */
@RestController("User-DocumentController")
@RequestMapping("/user/document")
@Validated
@Schema(description = "User - 协作文档管理")
@Tag(name = "User-协作文档管理", description = "用户端协作文档元数据接口")
@ConditionalOnProperty(prefix = "jacolp.document", name = "enabled", havingValue = "true")
public class DocumentController {

    private final DocumentMetadataService metadataService;

    public DocumentController(DocumentMetadataService metadataService) {
        this.metadataService = Objects.requireNonNull(metadataService, "metadataService must not be null");
    }

    @PostMapping
    @Operation(summary = "创建协作文档")
    public Result<DocumentMetadata> create(@RequestBody @Valid DocumentCreateRequest request) {
        return Result.success(metadataService.create(BaseContext.getCurrentId(), request.title()));
    }

    @GetMapping
    @Operation(summary = "查询当前用户的协作文档列表")
    public Result<List<DocumentMetadata>> list() {
        return Result.success(metadataService.list(BaseContext.getCurrentId()));
    }

    @GetMapping("/{documentId}/meta")
    @Operation(summary = "查询协作文档元数据")
    public Result<DocumentMetadata> getMetadata(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        return Result.success(metadataService.get(BaseContext.getCurrentId(), documentId));
    }

    @PatchMapping("/{documentId}/meta")
    @Operation(summary = "修改协作文档标题")
    public Result<DocumentMetadata> updateMetadata(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId,
            @RequestBody @Valid DocumentMetadataUpdateRequest request) {
        return Result.success(metadataService.updateTitle(BaseContext.getCurrentId(), documentId, request.title()));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "逻辑删除没有活跃协作会话的文档")
    public Result<Void> delete(
            @Parameter(description = "文档 ID") @PathVariable @Positive long documentId) {
        metadataService.delete(BaseContext.getCurrentId(), documentId);
        return Result.success();
    }
}
