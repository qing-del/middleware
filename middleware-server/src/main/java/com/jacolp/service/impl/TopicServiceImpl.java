package com.jacolp.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.TopicConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.pojo.domain.TopicNoteCountDO;
import com.jacolp.pojo.dto.TopicAddDTO;
import com.jacolp.pojo.dto.TopicListDTO;
import com.jacolp.pojo.dto.TopicModifyDTO;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.vo.TopicDetailVO;
import com.jacolp.pojo.vo.TopicListVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.TopicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Topic 领域服务实现。
 *
 * 约束：
 * 1. 所有查询和修改都按当前登录用户隔离（user_id）。
 * 2. 删除主题前必须校验该主题下无未删除笔记。
 */
@Service
@Slf4j
public class TopicServiceImpl implements TopicService {

    @Autowired
    private TopicMapper topicMapper;

    /**
     * 新增主题。
     */
    @Override
    public void addTopic(TopicAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String topicName = normalizeTopicName(dto.getTopicName());
        validateTopicName(topicName);

        // 同一用户下主题名唯一
        TopicEntity existed = topicMapper.selectByUserIdAndTopicName(userId, topicName);
        if (existed != null) {
            throw new BaseException(TopicConstant.TOPIC_ALREADY_EXISTS);
        }

        TopicEntity topic = new TopicEntity();
        BeanUtils.copyProperties(dto, topic);
        //topic.setUserId(userId);

        // 未传排序时使用默认值，保证列表排序稳定
        Integer sortOrder = dto.getSortOrder();
        if (sortOrder == null) {
            topic.setSortOrder(TopicConstant.DEFAULT_SORT_ORDER);
        } else {
            topic.setSortOrder(sortOrder);
        }

        int count = topicMapper.insertTopic(topic);
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ADD_FAILED);
        }
    }

    /**
     * 修改主题（名称、排序）。
     */
    @Override
    public void modifyTopic(TopicModifyDTO dto) {
        Long userId = BaseContext.getCurrentId();
        validateTopicId(dto.getId());

        // 业务约束：主题不存在则不允许修改
        TopicEntity existed = topicMapper.selectById(dto.getId());
        if (existed == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        if (StringUtils.hasText(dto.getTopicName())) {
            // 获取主题名称并校验
            String topicName = normalizeTopicName(dto.getTopicName());
            validateTopicName(topicName);

            // 仅在名称发生变化时做重复校验，避免无意义查询
            if (!topicName.equals(existed.getTopicName())) {
                TopicEntity duplicateCheckTarget = topicMapper.selectByUserIdAndTopicName(userId, topicName);
                if (duplicateCheckTarget != null && !duplicateCheckTarget.getId().equals(dto.getId())) {
                    throw new BaseException(TopicConstant.TOPIC_ALREADY_EXISTS);
                }
            }
            existed.setTopicName(topicName);
        }

        if (dto.getSortOrder() != null) {
            existed.setSortOrder(dto.getSortOrder());
        }

        int count = topicMapper.updateTopic(existed);
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_UPDATE_FAILED);
        }
    }

    /**
     * 通过 ID 查询主题详情。
     */
    @Override
    public TopicDetailVO getTopicById(Long id) {
        validateTopicId(id);

        TopicEntity topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        TopicDetailVO vo = new TopicDetailVO();
        BeanUtils.copyProperties(topic, vo);
        return vo;
    }

    /**
     * 分页查询主题列表。
     */
    @Override
    public PageResult listTopics(TopicListDTO dto) {
        if (dto == null) {
            dto = new TopicListDTO();
        }

        // 获取分页参数
        Integer pageNumParam = dto.getPageNum();
        Integer pageSizeParam = dto.getPageSize();
        int pageNum = pageNumParam == null || pageNumParam <= 0 ? 1 : pageNumParam;
        int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? 10 : pageSizeParam;

        // PageHelper 必须在查询语句前调用
        PageHelper.startPage(pageNum, pageSize);

        Long userId = dto.getUserId();
        if (userId != null && userId <= 0) {
            throw new BaseException(UserConstant.NOT_FIND_USER);
        }

        List<TopicListVO> records = topicMapper.listByCondition(userId, normalizeKeyword(dto.getKeyword()));
        PageInfo<TopicListVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 批量删除主题。
     *
     * 删除策略：
     * - 先全量校验主题是否存在、是否可删
     * - 校验全部通过后再执行删除，减少回滚成本
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopics(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的主题 ID 列表不能为空");
        }

        List<TopicNoteCountDO> deleteChecks = topicMapper.selectDeleteChecksByIds(ids);
        if (deleteChecks.size() != ids.size()) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        // 业务约束：主题下有未删除笔记则不允许删除
        for (TopicNoteCountDO topic : deleteChecks) {
            if (topic.getNoteCount() != null && topic.getNoteCount() > 0) {
                throw new BaseException(TopicConstant.TOPIC_DELETE_NOT_ALLOWED_PREFIX
                        + topic.getTopicName()
                        + TopicConstant.TOPIC_DELETE_NOT_ALLOWED_SUFFIX);
            }
        }

        int count = topicMapper.deleteByIds(new ArrayList<>(ids));
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_DELETE_FAILED);
        }
    }

    /**
     * 获取主题名称并做必要的处理。
     * - 删除前后空格
     * - 长度不能超过 25 个字符
     */
    private String normalizeTopicName(String topicName) {
        if (topicName == null) {
            return null;
        }
        // 防止前后空格导致“看起来相同、实际上不同”的主题名
        return topicName.trim();
    }

    /**
     * 获取关键词并做必要的处理。
     * - 删除前后空格
     */
    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 校验主题名称。
     * - 名称不能为空
     * - 长度不能超过 25 个字符
     */
    private void validateTopicName(String topicName) {
        /*
         * 这里先做应用层校验：
         * - 更早返回明确错误信息
         * - 避免无意义数据库交互
         */
        if (!StringUtils.hasText(topicName)) {
            throw new BaseException(TopicConstant.TOPIC_NAME_REQUIRED);
        }
        if (topicName.length() > TopicConstant.MAX_TOPIC_NAME_LENGTH) {
            throw new BaseException(TopicConstant.TOPIC_NAME_TOO_LONG);
        }
    }

    /**
     * 校验主题 ID。
     * - ID 不能为空
     * - ID 必须大于 0
     */
    private void validateTopicId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ID_INVALID);
        }
    }
}