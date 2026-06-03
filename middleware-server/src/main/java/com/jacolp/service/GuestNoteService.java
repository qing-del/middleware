package com.jacolp.service;

import com.jacolp.pojo.dto.note.GuestNoteQueryDTO;
import com.jacolp.pojo.vo.note.GuestNoteDetailVO;
import com.jacolp.result.PageResult;

public interface GuestNoteService {

    /**
     * 列出公开笔记
     * @param dto 查询参数
     * @return 笔记列表
     */
    PageResult listPublishedNotes(GuestNoteQueryDTO dto);

    /**
     * 获取公开笔记详情
     * @param noteId 笔记ID
     * @return 笔记详情
     */
    GuestNoteDetailVO getPublishedNoteDetail(Long noteId);
}
