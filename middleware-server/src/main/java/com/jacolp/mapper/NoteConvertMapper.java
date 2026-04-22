package com.jacolp.mapper;

import com.jacolp.pojo.entity.NoteConvertedEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteConvertMapper {

    NoteConvertedEntity selectByNoteId(@Param("noteId") Long noteId);

    int countByNoteId(@Param("noteId") Long noteId);

    int upsertConverted(NoteConvertedEntity entity);

    int deleteByNoteId(@Param("noteId") Long noteId);

    int deleteByNoteIds(@Param("noteIds") List<Long> noteIds);
}