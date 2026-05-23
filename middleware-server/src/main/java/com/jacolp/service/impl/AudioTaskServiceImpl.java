package com.jacolp.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.jacolp.constant.AudioConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.RateLimitExceededException;
import com.jacolp.mapper.ApiDailyUsageMapper;
import com.jacolp.mapper.AudioTaskMapper;
import com.jacolp.mapper.RoleMapper;
import com.jacolp.mapper.UserMapper;
import com.jacolp.pojo.dto.audio.AudioCallbackFinishDTO;
import com.jacolp.pojo.dto.audio.AudioCallbackStartDTO;
import com.jacolp.pojo.dto.audio.AudioTaskPageQueryDTO;
import com.jacolp.pojo.dto.audio.AudioTaskSubmitDTO;
import com.jacolp.pojo.entity.ApiDailyUsageEntity;
import com.jacolp.pojo.entity.AudioTaskEntity;
import com.jacolp.pojo.entity.RoleEntity;
import com.jacolp.pojo.entity.UserEntity;
import com.jacolp.pojo.vo.audio.AudioTaskSubmitVO;
import com.jacolp.pojo.vo.audio.AudioTaskVO;
import com.jacolp.result.PageResult;
import com.jacolp.service.AudioTaskService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AudioTaskServiceImpl implements AudioTaskService {

    @Autowired private AudioTaskMapper audioTaskMapper;
    @Autowired private ApiDailyUsageMapper apiDailyUsageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private StringRedisTemplate redis;

    @PostConstruct
    public void initStreamGroup() {
        try {
            redis.opsForStream().createGroup(AudioConstant.REDIS_STREAM_KEY, AudioConstant.STREAM_GROUP);
            log.info("Redis Stream group '{}' created", AudioConstant.STREAM_GROUP);
        } catch (DataAccessException e) {
            // 消费者组已存在，忽略
            log.debug("Redis Stream group already exists: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();

        // 校验 noiseType 合法性
        if (!AudioConstant.VALID_NOISE_TYPES.contains(dto.getNoiseType())) {
            throw new BaseException("不支持的背景音类型: " + dto.getNoiseType());
        }

        // 检查每日配额
        checkDailyQuota(userId);

        // 构建任务实体
        AudioTaskEntity task = new AudioTaskEntity();
        task.setUserId(userId);
        task.setSourceText(dto.getText());
        task.setSpeed(dto.getSpeed());
        task.setNoiseType(dto.getNoiseType());
        task.setNoiseFactor(dto.getNoiseFactor() != null
                ? dto.getNoiseFactor()
                : BigDecimal.valueOf(AudioConstant.DEFAULT_NOISE_FACTOR));
        task.setStatus(AudioConstant.TASK_STATUS_PENDING);

        audioTaskMapper.insert(task);
        log.info("Audio task created, taskId: {}, userId: {}", task.getId(), userId);

        // 推入 Redis Stream
        pushToStream(task);

        return new AudioTaskSubmitVO(task.getId(), AudioConstant.TASK_STATUS_PENDING);
    }

    @Override
    public boolean callbackStart(AudioCallbackStartDTO dto) {
        int updated = audioTaskMapper.casUpdateStatus(
                dto.getTaskId(),
                AudioConstant.TASK_STATUS_PENDING,
                AudioConstant.TASK_STATUS_PROCESSING,
                null, null, null);
        if (updated == 0) {
            log.warn("callbackStart CAS failed, taskId: {} (already processed or not found)", dto.getTaskId());
            return false;
        }
        log.info("Audio task started processing, taskId: {}", dto.getTaskId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean callbackFinish(AudioCallbackFinishDTO dto) {
        if (dto.getStatus() != AudioConstant.TASK_STATUS_SUCCESS
                && dto.getStatus() != AudioConstant.TASK_STATUS_FAILED) {
            throw new BaseException("无效的回调状态值: " + dto.getStatus());
        }

        LocalDate completedDate = dto.getStatus() == AudioConstant.TASK_STATUS_SUCCESS
                ? LocalDate.now() : null;


        int updated = audioTaskMapper.casUpdateStatus(
                dto.getTaskId(),
                AudioConstant.TASK_STATUS_PROCESSING,
                dto.getStatus(),
                dto.getResultUrl(),
                dto.getErrorMsg(),
                completedDate);


        if (updated == 0) {
            log.warn("callbackFinish CAS failed, taskId: {} (already processed or not found)", dto.getTaskId());

            // 更新用户使用额度
            Long userId = audioTaskMapper.getUserIdByTaskId(dto.getTaskId());
            LocalDate today = LocalDate.now();
            // 原子递减（并发安全）
            apiDailyUsageMapper.decrementUsage(userId, today);

            return false;
        }
        log.info("Audio task finished, taskId: {}, status: {}", dto.getTaskId(), dto.getStatus());
        return true;
    }

    @Override
    public AudioTaskVO getTask(Long taskId) {
        Long userId = BaseContext.getCurrentId();
        AudioTaskEntity task = audioTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BaseException("任务不存在或无权访问");
        }
        AudioTaskVO vo = new AudioTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    /**
     * 获取任务列表
     * <p>- 使用 {@link PermissionContext#isAdmin()} 做管理员校验</p>
     * <p>- 如果不是管理员，会使用 {@link BaseContext#getCurrentId()} 来做资源重定向</p>
     */
    @Override
    public PageResult listTasks(AudioTaskPageQueryDTO dto) {
        // 非管理员只能查询自己的任务，管理员可按 userId 筛选或查询全部
        if (!PermissionContext.isAdmin()) {
            dto.setUserId(BaseContext.getCurrentId());
        }

        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<AudioTaskEntity> list = audioTaskMapper.selectByUserId(dto);
        Page<AudioTaskEntity> p = (Page<AudioTaskEntity>) list;
        List<AudioTaskVO> voList = list.stream().map(task -> {
            AudioTaskVO vo = new AudioTaskVO();
            BeanUtils.copyProperties(task, vo);
            return vo;
        }).collect(Collectors.toList());

        return new PageResult(p.getTotal(), voList);
    }

    @Override
    public void requeueTask(AudioTaskEntity task) {
        pushToStream(task);
    }


    // ---- private helpers ----

    /**
     * 检查用户今日 API 调用次数
     * @param userId
     */
    private void checkDailyQuota(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        RoleEntity role = roleMapper.getById(user.getRoleId());
        int dailyLimit = role.getDailyApiLimit();

        LocalDate today = LocalDate.now();
        ApiDailyUsageEntity usage = apiDailyUsageMapper.selectByUserIdAndDate(userId, today);
        int currentCount = (usage != null) ? usage.getUsedCount() : 0;

        if (currentCount >= dailyLimit) {
            throw new RateLimitExceededException(
                    String.format("今日 API 调用次数已达上限（%d 次），请明日再试", dailyLimit));
        }

        // 原子递增（并发安全）
        apiDailyUsageMapper.incrementUsage(userId, today);
    }

    /**
     * 推送音频任务到 Redis Stream 消息队列
     */
    private void pushToStream(AudioTaskEntity task) {
        Map<String, String> payload = new HashMap<>();
        payload.put("taskId", String.valueOf(task.getId()));
        payload.put("userId", String.valueOf(task.getUserId()));
        payload.put("speed", task.getSpeed().toPlainString());
        payload.put("noiseType", task.getNoiseType());
        payload.put("noiseFactor", task.getNoiseFactor().toPlainString());
        payload.put("text", task.getSourceText());
        redis.opsForStream().add(AudioConstant.REDIS_STREAM_KEY, payload);
        log.debug("Audio task pushed to stream, taskId: {}", task.getId());
    }
}
