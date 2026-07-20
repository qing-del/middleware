package com.jacolp.middleware.module.note.biz.application.service;

import java.util.List;

import com.jacolp.middleware.module.note.biz.application.vo.note.NoteConvertResultVO;
import com.jacolp.middleware.module.note.biz.infrastructure.persistence.dataobject.NoteContextDO;
import com.jacolp.middleware.module.note.biz.infrastructure.persistence.dataobject.NoteDO;

public interface NoteConvertService {
    String convertAndSave(NoteDO note, NoteContextDO context);

    void delete(Long noteId);

    void deleteAllByNoteIds(List<Long> noteIds);

    NoteConvertResultVO getNoteConvert(Long noteId);

    NoteConvertResultVO getPublishedNoteConvert(Long noteId);
}
