package com.jacolp.service;

import java.util.List;

import com.jacolp.context.PermissionContext;
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

    /**
     * 通过 ID 查询主题详情。
     * <p>- 通过使用 {@link PermissionContext#isAdmin()} 来判断是否需要校验所有权</p>
     */
    TopicDetailVO getTopicById(Long id);

    PageResult listTopics(TopicListDTO dto);

    /**
     * 批量删除主题。
     * <ol>
     *     <il>- 先全量校验主题是否存在、是否可删</il>
     *     <il>- 校验全部通过后再执行删除，减少回滚成本</il>
     * </ol>
     * <p>- 通过 {@link PermissionContext#isAdmin()} 来判断是否需要进行所有权校验</p>
     */
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

    boolean topicExists(Long topicId);

    int updatePassStatusByIds(List<Long> ids, Short isPass);
}