package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.jacolp.pojo.entity.NoteImageMappingEntity;

@Mapper
public interface NoteImageMappingMapper {

    List<NoteImageMappingEntity> selectByNoteId(@Param("noteId") Long noteId);

    NoteImageMappingEntity selectById(@Param("id") Long id);

    /**
     * 根据 id 查询笔记图片映射行，并验证用户 id
     * @param mappingId 笔记图片映射 id
     * @param userId 用户 id（传入null的话不开启校验）
     * @return 笔记图片映射行 （不存在 / 没有所属权的时候返回 null）
     */
    NoteImageMappingEntity selectByIdWithValidUserId(Long mappingId, Long userId);

    int bindImageById(@Param("id") Long id,
                      @Param("imageId") Long imageId,
                      @Param("imageUserId") Long imageUserId,
                      @Param("isCrossUser") Short isCrossUser,
                      @Param("isPass") Short isPass);

    int batchBindImageByIds(@Param("mappings") List<NoteImageMappingEntity> mappings);

    int unbindImageById(@Param("id") Long id);

    /**
     * 批量插入/更新数据库表的数据
     * <p>需要保证存在 `note_id` 和 `image_id`</p>
     * <p>否则会造成数据拷贝的后果</p>
     * @param mappings
     * @return
     */
    int batchInsertMappings(@Param("mappings") List<NoteImageMappingEntity> mappings);

    int softDeleteByNoteId(@Param("noteId") Long noteId);

    int softDeleteByNoteIds(@Param("noteIds") List<Long> noteIds);

    int softDeleteByNoteIdAndParsedImageNames(@Param("noteId") Long noteId,
                                              @Param("parsedImageNames") List<String> parsedImageNames);

    int deleteSoftDeletedRows();

    /**
     * 统计指定笔记的图片映射数量
     * @param noteId 笔记 id
     * @param isPass 是否通过 （传入null代表全查）
     * @return 笔记图片映射行数量
     */
    int countByNoteIdAndPass(@Param("noteId") Long noteId,
                             @Param("isPass") Short isPass);

    int updateByImageIds(@Param("imageIds") List<Long> imageIds,
                         @Param("status") Short status);

    /**
     * 统计指定笔记的未绑定图片数量
     * @param noteId 笔记 id
     * @return 未绑定图片映射行数量
     */
    int countByNoteIdAndImageIdIsNull(@Param("noteId") Long noteId);
}