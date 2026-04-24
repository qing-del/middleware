package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jacolp.pojo.entity.NoteTagMappingEntity;

@Mapper
public interface NoteTagMappingMapper {

    List<NoteTagMappingEntity> selectByNoteId(@Param("noteId") Long noteId);

    NoteTagMappingEntity selectById(@Param("id") Long id);

    List<Long> selectTagIdsByNoteId(@Param("noteId") Long noteId);

    int bindTagById(@Param("id") Long id,
                    @Param("tagId") Long tagId,
                    @Param("isPass") Short isPass);

    int batchBindTagByIds(@Param("mappings") List<NoteTagMappingEntity> mappings);

    int unbindTagById(@Param("id") Long id);

    int batchInsertMappings(@Param("mappings") List<NoteTagMappingEntity> mappings);

    int softDeleteByNoteId(@Param("noteId") Long noteId);

    int softDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int softDeleteByNoteIdAndTagIds(@Param("noteId") Long noteId, @Param("tagIds") List<Long> tagIds);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的标签数量
     * @param noteId 笔记 id
     * @param isPass 是否通过 （传入null代表全查）
     * @return 笔记标签映射行数量
     */
    int countByNoteIdAndPass(@Param("noteId") Long noteId,
                             @Param("isPass") Short isPass);


    int updateByTagIds(@Param("tagIds") List<Long> tagIds, @Param("status") Short status);
}