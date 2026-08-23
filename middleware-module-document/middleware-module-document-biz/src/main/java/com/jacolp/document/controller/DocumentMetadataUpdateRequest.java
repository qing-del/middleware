package com.jacolp.document.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for the metadata fields that v0.3 permits clients to change. */
public record DocumentMetadataUpdateRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 255, message = "文档标题不能超过 255 个字符")
        String title
) {
}
