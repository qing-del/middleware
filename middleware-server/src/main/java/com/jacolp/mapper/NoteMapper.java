package com.jacolp.mapper;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.dto.note.NoteQueryDTO;
import com.jacolp.pojo.entity.NoteEntity;
import com.jacolp.pojo.vo.note.NoteSimpleVO;
import com.jacolp.pojo.vo.note.NoteVO;

@Mapper
public interface NoteMapper {

    @Select("select ifnull(sum(md_file_size), 0) from biz_note where user_id = #{userId} and is_deleted = 0")
    Long sumNoteFileSizeByUserId(@Param("userId") Long userId);

    int insertNote(NoteEntity note);

    int updateNote(NoteEntity note);

    @Select("select id, user_id, topic_id, title, description, is_published, storage_type, is_missing_info, is_pass, is_deleted, md_file_size, create_time, update_time from biz_note where id = #{id}")
    NoteEntity selectById(@Param("id") Long id);

    /**
     * 查询笔记VO
     * <p>与 topic 表进行了联查</p>
     * @param id
     * @return
     */
    NoteVO selectVoById(@Param("id") Long id);

    List<NoteEntity> selectByIds(@Param("ids") List<Long> ids);

    List<NoteVO> listByCondition(NoteQueryDTO dto);

    NoteEntity selectByUserIdAndTitle(@Param("userId") Long userId, @Param("title") String title);

    /**
     * 批量查询指定用户、指定话题下的笔记（命中联合唯一索引）
     * <p>索引：(user_id, topic_id, title(30), is_deleted)</p>
     * @param userId  用户 ID
     * @param topicId 话题 ID
     * @param titles  笔记标题列表
     * @return 匹配到的笔记列表
     */
    List<NoteEntity> selectByUserIdAndTopicIdAndTitles(@Param("userId") Long userId,
                                                       @Param("topicId") Long topicId,
                                                       @Param("titles") List<String> titles);

    ArrayList<NoteSimpleVO> selectNoteSimpleByImageId(@Param("imageId") Long imageId);

    int updatePublishStatus(@Param("id") Long id, @Param("isPublished") Short isPublished);

    int updateMissingInfo(@Param("id") Long id, @Param("isMissingInfo") Short isMissingInfo);

    int softDeleteByIds(@Param("ids") List<Long> ids);

    int updatePassByIds(@Param("ids") List<Long> ids,
                        @Param("isPass") Short isPass);

    /**
     * 查询用户下指定话题且指定名字的笔记数量
     * <p>只有 `is_deleted = 0` 才会被归纳为存在</p>
     * @param userId 用户 ID
     * @param topicId 主题 ID
     * @param originalFilename 原始文件名
     * @return 是否存在 1-存在 0-不存在
     */
    @Select("select count(1) from biz_note where " +
            "user_id = #{userId} and " +
            "topic_id = #{topicId} and " +
            "title = #{originalFilename} and " +
            "is_deleted = 0")
    int countByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String originalFilename);

        /**
         * 统计指定用户的笔记总数（未删除）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND is_deleted = 0")
        long countByUserId(@Param("userId") Long userId);

        /**
         * 统计指定用户的公开笔记数量（未删除 + 已发布）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND is_deleted = 0 AND is_published = 1")
        long countPublicByUserId(@Param("userId") Long userId);

        /**
         * 统计指定用户已通过审核的笔记数量（未删除 + is_pass = 1）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND is_deleted = 0 AND is_pass = 1")
        long countApprovedByUserId(@Param("userId") Long userId);

    /**
     * 用户端条件查询：当前用户自己的笔记 + 别人已发布的笔记。
     * @param userId 用户 ID
     * @param topicId 主题 ID
     * @param title 笔记标题的关键词
     */
    List<NoteVO> listByUserCondition(@Param("userId") Long userId,
                                     @Param("topicId") Long topicId,
                                     @Param("title") String title);
}
