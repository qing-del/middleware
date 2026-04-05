package com.jacolp.service;

import com.jacolp.pojo.dto.TopicAddDTO;
import com.jacolp.pojo.dto.TopicListDTO;
import com.jacolp.pojo.dto.TopicModifyDTO;
import com.jacolp.pojo.vo.TopicDetailVO;
import com.jacolp.result.PageResult;

import java.util.List;

public interface TopicService {

    void addTopic(TopicAddDTO dto);

    void modifyTopic(TopicModifyDTO dto);

    TopicDetailVO getTopicById(Long id);

    PageResult listTopics(TopicListDTO dto);

    void deleteTopics(List<Long> ids);
}