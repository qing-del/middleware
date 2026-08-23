package com.jacolp.document.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for creating a document in the authenticated user's personal scope. */
public record DocumentCreateRequest(
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 255, message = "文档标题不能超过 255 个字符")
        String title
) {
}
