package com.jacolp.service;

import com.jacolp.pojo.vo.note.NoteConvertResultVO;

import java.util.List;

public interface NoteConvertService {
    /**
     * 将 md 笔记内容转换为 html，然后写入数据库
     * @param noteId
     * @param rawMarkdown
     * @return
     */
    String convertAndSave(Long noteId, String rawMarkdown);

    /**
     * 删除 笔记已转换的内容
     * @param noteId
     */
    void delete(Long noteId);

    /**
     * 批量删除笔记已转换的内容
     * @param noteIds
     */
    void deleteAllByNoteIds(List<Long> noteIds);

    /**
     * 获取笔记转换结果 -- (管理员)
     * <p>会越过是否发布的校验</p>
     * @param noteId 笔记 ID
     * @return 笔记转换结果
     */
    NoteConvertResultVO adminOpenNote(Long noteId);
}
