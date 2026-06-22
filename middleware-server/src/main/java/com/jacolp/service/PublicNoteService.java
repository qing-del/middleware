package com.jacolp.service;

import com.jacolp.pojo.dto.note.PublicNoteQueryDTO;
import com.jacolp.pojo.vo.note.PublicNoteDetailVO;
import com.jacolp.result.PageResult;

public interface PublicNoteService {

    /**
     * 分页列出公开笔记。
     */
    PageResult listPublishedNotes(PublicNoteQueryDTO dto);

    /**
     * 获取公开笔记详情。
     */
    PublicNoteDetailVO getPublishedNoteDetail(Long noteId);
}
