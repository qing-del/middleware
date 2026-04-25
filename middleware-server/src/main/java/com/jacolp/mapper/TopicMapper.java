package com.jacolp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.jacolp.pojo.domain.TopicNoteCountDO;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.vo.topic.TopicListVO;

@Mapper
/**
 * 主题数据访问层。
 */
public interface TopicMapper {

    /**
     * 按主题 ID + 用户 ID 查询，保证数据隔离。
     */
    @Select("SELECT id, user_id, topic_name, sort_order, create_time, update_time FROM biz_topic WHERE id = #{id}")
    TopicEntity selectById(@Param("id") Long id);

    /**
     * 查询同用户下是否存在同名主题。
     */
    @Select("SELECT id, user_id, topic_name, sort_order, create_time, update_time FROM biz_topic WHERE user_id = #{userId} AND topic_name = #{topicName}")
    TopicEntity selectByUserIdAndTopicName(@Param("userId") Long userId, @Param("topicName") String topicName);

    /**
     * 新增主题。
     */
    int insertTopic(TopicEntity topic);

    /**
     * 修改主题。
     */
    int updateTopic(TopicEntity topic);

    /**
     * 条件分页查询主题列表。
     */
    List<TopicListVO> listByCondition(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 删除前校验：查询目标主题及其未删除笔记数。
     */
    List<TopicNoteCountDO> selectDeleteChecksByIds(@Param("ids") List<Long> ids);

    /**
     * 批量删除主题。
     */
    int deleteByIds(@Param("ids") List<Long> ids);

    int updatePassByIds(@Param("ids") List<Long> ids,
                        @Param("isPass") Short isPass);

    /**
     * 按 ID 检查主题是否存在。
     */
    @Select("SELECT IFNULL(COUNT(1), 0) FROM biz_topic WHERE id = #{topicId}")
    int countById(Long topicId);

    /**
     * 用户端条件查询：当前用户自己的主题 + 别人已通过审核的主题。
     */
    List<TopicListVO> listByUserCondition(@Param("userId") Long userId, @Param("keyword") String keyword);
}