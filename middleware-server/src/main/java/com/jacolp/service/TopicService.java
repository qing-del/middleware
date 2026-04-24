package com.jacolp.service;

import com.jacolp.pojo.dto.TopicAddDTO;
import com.jacolp.pojo.dto.TopicListDTO;
import com.jacolp.pojo.dto.TopicModifyDTO;
import com.jacolp.pojo.dto.UserTopicQueryDTO;
import com.jacolp.pojo.vo.TopicDetailVO;
import com.jacolp.result.PageResult;

import java.util.List;

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
}