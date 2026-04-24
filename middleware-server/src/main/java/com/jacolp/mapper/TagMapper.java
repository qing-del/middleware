package com.jacolp.mapper;

import com.jacolp.pojo.domain.TagNoteCountDO;
import com.jacolp.pojo.entity.TagEntity;
import com.jacolp.pojo.vo.TagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper {

    int insertTag(TagEntity tag);

    int batchInsertTags(@Param("tags") List<TagEntity> tags);

    @Select("SELECT tag_name FROM biz_tag WHERE user_id = #{userId}")
    List<String> selectTagNamesByUserId(@Param("userId") Long userId);

    @Select("SELECT id, user_id, tag_name, is_pass, create_time FROM biz_tag WHERE user_id = #{userId} AND tag_name = #{tagName}")
    TagEntity selectByUserIdAndTagName(@Param("userId") Long userId, @Param("tagName") String tagName);

    @Select("SELECT id, user_id, tag_name, is_pass, create_time FROM biz_tag WHERE id = #{id} AND user_id = #{userId}")
    TagEntity selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<TagEntity> selectByIds(@Param("ids") List<Long> ids);

    int updateTag(TagEntity tag);

    List<TagNoteCountDO> selectDeleteChecksByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

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
}