package com.jacolp.service;

import com.jacolp.pojo.entity.NoteContextEntity;

import java.util.List;

public interface NoteContextService {

    /**
     * 获取笔记源文件 -- (管理员专属)
     * @param noteId 笔记 ID
     * @return
     */
    String adminGetSource(Long noteId);

    /**
     * 获取笔记源文件
     * @param noteId 笔记 ID
     * @return
     */
    NoteContextEntity getByNoteId(Long noteId);

    /**
     * 保存笔记
     * @param noteContext 笔记文本实体
     */
    void insert(NoteContextEntity noteContext);

    /**
     * 更新笔记
     * @param noteContext 笔记文本实体
     */
    void update(NoteContextEntity noteContext);

    /**
     * 批量删除笔记内容
     * @param noteIds 笔记 ID
     */
    void deleteByNoteIds(List<Long> noteIds);
}
