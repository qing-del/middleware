package com.jacolp.mapper;

import java.util.List;

import com.jacolp.pojo.dto.tag.TagNoteCountDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.tag.TagVO;

@Mapper
public interface TagMapper {

    int insertTag(TagEntity tag);

    int batchInsertTags(@Param("tags") List<TagEntity> tags);

    @Select("SELECT tag_name FROM biz_tag WHERE user_id = #{userId}")
    List<String> selectTagNamesByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, tag_name, is_pass, create_time FROM biz_tag WHERE user_id = #{userId}")
    List<TagEntity> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, tag_name, is_pass, create_time FROM biz_tag WHERE user_id = #{userId} AND tag_name = #{tagName}")
    TagEntity selectByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);

    @Select("SELECT id, user_id, tag_name, is_pass, create_time FROM biz_tag WHERE id = #{id} AND user_id = #{userId}")
    TagEntity selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<TagEntity> selectByIds(@Param("ids") List<Long> ids);

    int updateTag(TagEntity tag);

    List<TagNoteCountDTO> selectDeleteChecksByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    int deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    int updatePassByIds(@Param("ids") List<Long> ids,
                        @Param("isPass") Short isPass);

    /**
     * 根据条件查询标签
     * @param userId
     * @param keyword
     * @return
     */
    List<TagVO> listByCondition(@Param("userId") Long userId, @Param("keyword") String keyword);

    List<TagEntity> selectIdsByNamesAndUserId(List<String> tagNames, Long userId);

    /**
     * 用户端条件查询：当前用户自己的标签 + 别人已通过审核的标签。
     */
    List<TagVO> listByUserCondition(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 根据标签id查询标签
     * @param tagId
     * @return
     */
    @Select("SELECT * FROM biz_tag WHERE id = #{tagId}")
    TagEntity selectById(Long tagId);

    /**
     * 统计指定用户的标签数量。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_tag WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    /**
     * 统计指定用户已通过审核的标签数量。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_tag WHERE user_id = #{userId} AND is_pass = 1")
    long countPassedByUserId(@Param("userId") Long userId);
}