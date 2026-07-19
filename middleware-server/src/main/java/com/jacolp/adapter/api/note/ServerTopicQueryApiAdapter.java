package com.jacolp.adapter.api.note;

import com.jacolp.mapper.TopicMapper;
import com.jacolp.middleware.module.note.api.TopicQueryApi;
import com.jacolp.pojo.entity.TopicEntity;
import org.springframework.stereotype.Component;

/**
 * Transitional topic ownership query backed by the legacy mapper.
 */
@Component
public class ServerTopicQueryApiAdapter implements TopicQueryApi {

    private final TopicMapper topicMapper;

    public ServerTopicQueryApiAdapter(TopicMapper topicMapper) {
        this.topicMapper = topicMapper;
    }

    @Override
    public boolean isOwnedBy(Long topicId, Long userId) {
        if (!isPositive(topicId) || !isPositive(userId)) {
            return false;
        }
        TopicEntity topic = topicMapper.selectById(topicId);
        return topic != null && userId.equals(topic.getUserId());
    }

    private static boolean isPositive(Long id) {
        return id != null && id > 0;
    }
}
