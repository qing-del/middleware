package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jacolp.pojo.entity.NoteTagMappingEntity;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoteTagMappingMapper {

    List<NoteTagMappingEntity> selectByNoteId(@Param("noteId") Long noteId);

    NoteTagMappingEntity selectById(@Param("id") Long id);

    /**
     * 根据 id 查询笔记标签映射行，并验证用户 id
     * @param mappingId 笔记标签映射 id
     * @param userId 用户 id（传入null的话不开启校验）
     * @return 笔记标签映射行 （不存在 / 没有所属权的时候返回 null）
     */
    NoteTagMappingEntity selectByIdWithValidUserId(Long mappingId, Long userId);

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

    /**
     * 统计指定笔记的未绑定标签数量
     * @param noteId 笔记 id
     * @return 未绑定标签映射行数量
     */
    int countByNoteIdAndTargetIdIsNull(@Param("noteId") Long noteId);

    /**
     * 统计指定标签的笔记数量
     * @param tagId 标签 id
     * @return 笔记标签映射行数量
     */
    @Select("SELECT COUNT(1) FROM biz_note_tag_mapping WHERE tag_id = #{tagId} AND is_deleted = 0")
    long countByTagId(Long tagId);
}