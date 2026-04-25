package com.jacolp.pojo.dto.note;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端删除笔记 DTO
 */
@Data
@Schema(description = "用户端删除笔记请求")
public class UserNoteDeleteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @Schema(description = "笔记ID", required = true)
    private Long id;
}