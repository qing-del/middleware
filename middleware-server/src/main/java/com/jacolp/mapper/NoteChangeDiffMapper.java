package com.jacolp.mapper;

import com.jacolp.pojo.entity.NoteChangeDiffEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoteChangeDiffMapper {

    /**
     * 根据笔记ID查询笔记修改差异
     * @param noteId 笔记ID
     * @return 笔记修改差异
     */
    NoteChangeDiffEntity selectByNoteId(@Param("noteId") Long noteId);

    int upsertDiff(NoteChangeDiffEntity entity);

    int updateStatus(@Param("noteId") Long noteId, @Param("status") Integer status);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int deleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    @Select("SELECT count(*) FROM biz_note_change_diff WHERE note_id = #{noteId} AND status = #{status}")
    int countByNoteIdAndStatus(Long noteId, Integer status);

    @Select("SELECT * FROM biz_note_change_diff WHERE note_id = #{noteId} AND status = #{status}")
    NoteChangeDiffEntity selectByNoteIdAndStatus(Long noteId, Integer status);
}