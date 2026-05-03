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

    @Select("select ifnull(sum(md_file_size), 0) from biz_note where user_id = #{userId} and status != 8")
    Long sumNoteFileSizeByUserId(@Param("userId") Long userId);

    int insertNote(NoteEntity note);

    int updateNote(NoteEntity note);

    @Select("select id, user_id, topic_id, title, description, storage_type, status, missing_info_mask, missing_count, md_file_size, create_time, update_time from biz_note where id = #{id}")
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
     * <p>索引：(user_id, topic_id, title(30), status)</p>
     * @param userId  用户 ID
     * @param topicId 话题 ID
     * @param titles  笔记标题列表
     * @return 匹配到的笔记列表
     */
    List<NoteEntity> selectByUserIdAndTopicIdAndTitles(@Param("userId") Long userId,
                                                       @Param("topicId") Long topicId,
                                                       @Param("titles") List<String> titles);

    ArrayList<NoteSimpleVO> selectNoteSimpleByImageId(@Param("imageId") Long imageId);

    int softDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 更新笔记状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    /**
     * 批量更新笔记状态
     */
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") Short status);

    /**
     * 更新缺失信息掩码和数量
     */
    int updateMissingInfoFields(@Param("id") Long id,
                                @Param("missingInfoMask") Integer mask,
                                @Param("missingCount") Integer count);

    /**
     * 更新缺失信息数量
     */
    int updateMissingCount(@Param("id") Long id, @Param("missingCount") Integer count);

    /**
     * 批量更新检查绑定时的字段
     * <p>一次性更新 status, missingInfoMask, missingCount</p>
     * @param id 笔记ID
     * @param status 笔记状态
     * @param missingInfoMask 缺失信息掩码
     * @param missingCount 缺失信息数量
     */
    int updateNoteFieldsForCheck(@Param("id") Long id,
                                  @Param("status") Short status,
                                  @Param("missingInfoMask") Integer missingInfoMask,
                                  @Param("missingCount") Integer missingCount);

    /**
     * 查询用户下指定话题且指定名字的笔记数量
     * <p>只有 `status != 8` 才会被归纳为存在</p>
     * @param userId 用户 ID
     * @param topicId 主题 ID
     * @param originalFilename 原始文件名
     * @return 是否存在 1-存在 0-不存在
     */
    @Select("select count(1) from biz_note where " +
            "user_id = #{userId} and " +
            "topic_id = #{topicId} and " +
            "title = #{originalFilename} and " +
            "status != 8")
    int countByUserIdAndTopicIdAndTitle(Long userId, Long topicId, String originalFilename);

        /**
         * 统计指定用户的笔记总数（未删除）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status != 8")
        long countByUserId(@Param("userId") Long userId);

        /**
         * 统计指定用户的公开笔记数量（未删除 + 已发布）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status = 6")
        long countPublicByUserId(@Param("userId") Long userId);

        /**
         * 统计指定用户已通过审核的笔记数量（未删除 + 已通过/已公开）。
         */
        @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_note WHERE user_id = #{userId} AND status IN (5, 6)")
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

    /**
     * 查询指定笔记的笔记状态
     * @param noteId 笔记 ID
     * @return 笔记状态
     */
    @Select("SELECT status FROM biz_note WHERE id = #{noteId}")
    Short selectStatusById(Long noteId);
}
