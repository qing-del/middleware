package com.jacolp.service;

import com.jacolp.pojo.dto.note.GuestNoteQueryDTO;
import com.jacolp.pojo.vo.note.GuestNoteDetailVO;
import com.jacolp.result.PageResult;

public interface GuestNoteService {

    PageResult listPublishedNotes(GuestNoteQueryDTO dto);

    GuestNoteDetailVO getPublishedNoteDetail(Long noteId);
}
