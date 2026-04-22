package com.jacolp.mapper;

import com.jacolp.pojo.entity.NoteContextEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记内容表 Mapper
 */
@Mapper
public interface NoteContextMapper {

    /**
     * 根据 note_id 查询笔记内容
     */
    NoteContextEntity selectByNoteId(Long noteId);

    /**
     * 新增笔记内容
     */
    int insertContext(NoteContextEntity entity);

    /**
     * 更新笔记内容（UPSERT 语义）
     */
    int updateContext(NoteContextEntity entity);

    /**
     * 删除笔记内容
     */
    int deleteByNoteId(Long noteId);

    /**
     * 批量删除笔记内容
     */
    int deleteByNoteIds(@Param("noteIds") List<Long> noteIds);
}
