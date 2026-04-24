package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jacolp.pojo.entity.NoteEachMappingEntity;

@Mapper
public interface NoteEachMappingMapper {

    List<NoteEachMappingEntity> selectBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);

    NoteEachMappingEntity selectById(@Param("id") Long id);

    int bindNoteById(@Param("id") Long id,
                     @Param("targetNoteId") Long targetNoteId,
                     @Param("isPass") Short isPass);

    int batchBindNoteByIds(@Param("mappings") List<NoteEachMappingEntity> mappings);

    int unbindNoteById(@Param("id") Long id);

    int batchInsertMappings(@Param("mappings") List<NoteEachMappingEntity> mappings);

    int softDeleteBySourceNoteId(@Param("sourceNoteId") Long sourceNoteId);

    int softDeleteBySourceNoteIds(@Param("sourceNoteIds") List<Long> sourceNoteIds);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的内联笔记映射数量
     * @param noteId 笔记 id
     * @param isPass 是否通过 （传入null代表全查）
     * @return 笔记内联笔记映射行数量
     */
    int countByNoteIdAndPass(@Param("noteId") Long noteId,
                             @Param("isPass") Short isPass);

    int updateBySourceNoteIds(@Param("sourceNoteIds") List<Long> sourceNoteIds,
                              @Param("status") Short status);
}