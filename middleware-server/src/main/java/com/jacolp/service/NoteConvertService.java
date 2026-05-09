package com.jacolp.service;

import com.jacolp.exception.BaseException;
import com.jacolp.pojo.vo.note.NoteConvertResultVO;

import java.util.List;

public interface NoteConvertService {
    /**
     * 将 Markdown 原文转换为 HTML 并写入数据库。
     * @param noteId      笔记 ID
     * @param rawMarkdown Markdown 原文
     * @return 解析出的标题（可能不同于文件名）
     * @throws BaseException 写入数据库失败的时候会抛出此异常
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
     * 获取笔记转换结果
     * <p>通过 {@link com.jacolp.context.PermissionContext} 来判断是否进行所属权校验</p>
     * <p>会越过是否发布的校验 - 不适合用作打开公共笔记的逻辑方法</p>
     * @param noteId 笔记 ID
     * @return 笔记转换结果
     * @throws BaseException 笔记转换 结果不存在 / 无权限访问 的时候会抛出此异常
     */
    NoteConvertResultVO getNoteConvert(Long noteId);
}
