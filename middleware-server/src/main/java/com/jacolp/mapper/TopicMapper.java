package com.jacolp.mapper;

import com.jacolp.pojo.domain.TopicNoteCountDO;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.vo.TopicListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}