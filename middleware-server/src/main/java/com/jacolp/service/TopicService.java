package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.dto.topic.TopicAddDTO;
import com.jacolp.pojo.dto.topic.TopicListDTO;
import com.jacolp.pojo.dto.topic.TopicModifyDTO;
import com.jacolp.pojo.dto.topic.UserTopicQueryDTO;
import com.jacolp.pojo.vo.topic.TopicDetailVO;
import com.jacolp.pojo.vo.topic.TopicStatsVO;
import com.jacolp.result.PageResult;

public interface TopicService {

    void addTopic(TopicAddDTO dto);

    void modifyTopic(TopicModifyDTO dto);

    TopicDetailVO getTopicById(Long id);

    PageResult listTopics(TopicListDTO dto);

    void deleteTopics(List<Long> ids);

    /**
     * 用户端条件查询：当前用户自己的主题 + 别人已通过审核的主题。
     */
    PageResult listUserTopics(UserTopicQueryDTO dto);

    /**
     * 用户端发起主题审核申请。
     */
    void submitTopicAudit(Long topicId);

    /**
     * 获取当前用户主题统计。
     */
    TopicStatsVO getUserTopicStats();
}