package com.jacolp.service;

import com.jacolp.pojo.entity.NoteChangeDiffEntity;

import java.util.List;

public interface NoteChangeDiffService {
    /**
     * 获取 笔记是否存在特定状态的修改记录
     * <p>- NoteConstant.NOTE_DIFF_STATUS_PENDING = 0 表示处于待确认</p>
     * <p>- NoteConstant.NOTE_DIFF_STATUS_CONFIRMED = 1 表示已确认</p>
     * <p>- NoteConstant.NOTE_DIFF_STATUS_CANCELED = 2 表示已取消</p>
     * @param noteId 笔记 ID
     * @param status 笔记状态
     * @return 笔记是否存在特定状态的修改记录
     */
    boolean countByNoteIdAndStatus(Long noteId, Integer status);


    /**
     * 插入 笔记修改diff记录
     * @param noteChangeDiffEntity 笔记修改记录
     */
    void insert(NoteChangeDiffEntity noteChangeDiffEntity);

    /**
     * 删除 笔记修改diff记录
     * @param noteIds 笔记ID列表
     */
    void deleteByNoteIds(List<Long> noteIds);
}
