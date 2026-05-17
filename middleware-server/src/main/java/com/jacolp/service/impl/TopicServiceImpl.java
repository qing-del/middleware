package com.jacolp.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.jacolp.context.PermissionContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.constant.AuditConstant;
import com.jacolp.constant.TopicConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.exception.BaseException;
import com.jacolp.mapper.TopicMapper;
import com.jacolp.pojo.dto.topic.TopicNoteCountDTO;
import com.jacolp.pojo.dto.topic.TopicAddDTO;
import com.jacolp.pojo.dto.topic.TopicListDTO;
import com.jacolp.pojo.dto.topic.TopicModifyDTO;
import com.jacolp.pojo.dto.topic.UserTopicQueryDTO;
import com.jacolp.pojo.entity.MetaAuditRecordEntity;
import com.jacolp.pojo.entity.TopicEntity;
import com.jacolp.pojo.vo.topic.TopicDetailVO;
import com.jacolp.pojo.vo.topic.TopicListVO;
import com.jacolp.pojo.vo.topic.TopicStatsVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.AuditService;
import com.jacolp.service.TopicService;

import lombok.extern.slf4j.Slf4j;

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

    @Autowired private TopicMapper topicMapper;
    @Autowired private AuditService auditService;

    /**
     * 新增主题。
     */
    @Override
    public void addTopic(TopicAddDTO dto) {
        Long userId = BaseContext.getCurrentId();
        String topicName = normalizeTopicName(dto.getTopicName());

        // 同一用户下主题名唯一
        TopicEntity existed = topicMapper.selectByUserIdAndTopicName(userId, topicName);
        if (existed != null) {
            throw new BaseException(TopicConstant.TOPIC_ALREADY_EXISTS);
        }

        TopicEntity topic = new TopicEntity();
        BeanUtils.copyProperties(dto, topic);
        topic.setUserId(userId);
        topic.setIsPass(AuditConstant.WAIT);    // 默认处于待审核

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
     * 修改主题（排序）。
     */
    @Override
    public void modifyTopic(TopicModifyDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 业务约束：主题不存在则不允许修改
        TopicEntity existed = topicMapper.selectById(dto.getId());
        if (existed == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
        // 检查是否有所属权
        if (!existed.getUserId().equals(userId)) {
            throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
        }

        // 设置排序等级
        if (dto.getSortOrder() != null) {
            existed.setSortOrder(dto.getSortOrder());
        }

        // 更新修改到 DB
        int count = topicMapper.updateTopic(existed);
        if (count <= 0) {
            throw new BaseException(TopicConstant.TOPIC_UPDATE_FAILED);
        }
    }

    /**
     * 通过 ID 查询主题详情。
     * <p>- 通过使用 {@link PermissionContext#isAdmin()} 来判断是否需要校验所有权</p>
     */
    @Override
    public TopicDetailVO getTopicById(Long id) {
        validateTopicId(id);

        TopicEntity topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        // 如果不是管理员需要做权限检查
        if (!PermissionContext.isAdmin()
        && !topic.getUserId().equals(BaseContext.getCurrentId())
        && !AuditConstant.PASS.equals(topic.getIsPass())) {
            throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
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


        // PageHelper 必须在查询语句前调用
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TopicListVO> records = topicMapper.listByCondition(dto.getUserId(), normalizeKeyword(dto.getKeyword()));
        PageInfo<TopicListVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 批量删除主题。
     * <ol>
     *     <il>- 先全量校验主题是否存在、是否可删</il>
     *     <il>- 校验全部通过后再执行删除，减少回滚成本</il>
     * </ol>
     * <p>- 通过 {@link PermissionContext#isAdmin()} 来判断是否需要进行所有权校验</p>
     * @param ids 主题 id 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopics(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BaseException("待删除的主题 ID 列表不能为空");
        }

        // 对 ID 列表进行去重
        ids = ids.stream().distinct().collect(Collectors.toList());

        // 从数据库中查询
        List<TopicNoteCountDTO> deleteChecks = topicMapper.selectDeleteChecksByIds(ids);
        if (deleteChecks.size() != ids.size()) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }

        // 如果不是管理员调用，还需要保证删除的主题都是属于当前用户
        if (!PermissionContext.isAdmin()) {
            for (TopicNoteCountDTO topic : deleteChecks) {
                if (!topic.getUserId().equals(BaseContext.getCurrentId())) {
                    throw new BaseException(TopicConstant.TOPIC_NOT_OWNER);
                }
            }
        }

        // 业务约束：主题下有未删除笔记则不允许删除
        for (TopicNoteCountDTO topic : deleteChecks) {
            if (topic.getNoteCount() != null && topic.getNoteCount() > 0) {
                throw new BaseException(TopicConstant.TOPIC_DELETE_NOT_ALLOWED_PREFIX
                        + topic.getTopicName()
                        + TopicConstant.TOPIC_DELETE_NOT_ALLOWED_SUFFIX);
            }
        }

        int count = topicMapper.deleteByIds(new ArrayList<>(ids));
        if (count < ids.size()) {
            throw new BaseException(TopicConstant.TOPIC_DELETE_FAILED);
        }
    }

    /**
     * 用户端条件查询：当前用户自己的主题 + 别人已通过审核的主题。
     */
    @Override
    public PageResult listUserTopics(UserTopicQueryDTO dto) {
        if (dto == null) {
            dto = new UserTopicQueryDTO();
        }
        Long userId = BaseContext.getCurrentId();

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<TopicListVO> records = topicMapper.listByUserCondition(userId, normalizeKeyword(dto.getKeyword()));
        PageInfo<TopicListVO> pageInfo = new PageInfo<>(records);
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }

    /**
     * 用户端发起主题审核申请。
     */
    @Override
    public void submitTopicAudit(Long topicId) {
        Long userId = BaseContext.getCurrentId();
        validateTopicId(topicId);

        TopicEntity topic = topicMapper.selectById(topicId);
        if (topic == null) {
            throw new BaseException(TopicConstant.TOPIC_NOT_FOUND);
        }
        if (!topic.getUserId().equals(userId)) {
            throw new BaseException("只能申请审核自己的主题");
        }
        if (AuditConstant.PASS.equals(topic.getIsPass())) {
            throw new BaseException("该主题已通过审核");
        }
        // 检查是否已有待审核记录
        if (auditService.hasPendingMetaAudit(AuditConstant.TOPIC_APPLY_TYPE, topicId)) {
            throw new BaseException("该主题已有待审核的申请");
        }

        MetaAuditRecordEntity record = new MetaAuditRecordEntity();
        record.setApplicantUserId(userId);
        record.setApplyType(AuditConstant.TOPIC_APPLY_TYPE);
        record.setTargetId(topicId);
        auditService.createMetaAuditRecord(record);
    }

    /**
     * 获取当前用户主题统计。
     */
    @Override
    public TopicStatsVO getUserTopicStats() {
        Long userId = BaseContext.getCurrentId();
        long topicCount = topicMapper.countByUserId(userId);
        long passedCount = topicMapper.countPassedByUserId(userId);
        return new TopicStatsVO(topicCount, passedCount);
    }

    @Override
    public boolean topicExists(Long topicId) {
        if (topicId == null || topicId <= 0) {
            return false;
        }
        return topicMapper.countById(topicId) > 0;
    }

    @Override
    public int updatePassStatusByIds(List<Long> ids, Short isPass) {
        return topicMapper.updatePassByIds(ids, isPass);
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
     * 校验主题 ID。
     * <p>- ID 不能为空</p>
     * <p>- ID 必须大于 0</p>
     */
    private void validateTopicId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(TopicConstant.TOPIC_ID_INVALID);
        }
    }
}