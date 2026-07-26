package com.jacolp.audio.biz.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.jacolp.audio.biz.domain.dto.AudioCallbackFinishDTO;
import com.jacolp.audio.biz.domain.dto.AudioCallbackStartDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.domain.dto.AudioTaskSubmitDTO;
import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.exception.BaseException;
import com.jacolp.exception.RateLimitExceededException;
import com.jacolp.audio.biz.constant.AudioConstant;
import com.jacolp.audio.biz.domain.vo.AudioTaskSubmitVO;
import com.jacolp.audio.biz.domain.vo.AudioTaskVO;
import com.jacolp.audio.biz.audio.AudioTaskLifecycle;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import com.jacolp.audio.biz.persistence.mapper.AudioTaskMapper;
import com.jacolp.module.system.api.quota.ConsumeQuotaCommand;
import com.jacolp.module.system.api.quota.ConsumeQuotaResult;
import com.jacolp.module.system.api.quota.UserQuotaApi;
import com.jacolp.result.PageResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AudioTaskServiceImpl implements AudioTaskService {
    @Autowired
    private AudioTaskMapper audioTaskMapper;
    @Autowired
    private UserQuotaApi userQuotaApi;
    @Autowired
    private AudioTaskPublisher audioTaskPublisher;
    @Autowired
    private TransactionAfterCommitExecutor transactionAfterCommitExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AudioTaskSubmitVO submitTask(AudioTaskSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        // 校验 noiseType 合法性
        if (!AudioConstant.VALID_NOISE_TYPES.contains(dto.getNoiseType()))
            throw new BaseException("不支持的背景音类型: " + dto.getNoiseType());
        // 检查每日配额
        checkDailyQuota(userId);
        // 构建任务实体
        AudioTaskDO task = new AudioTaskDO();
        task.setUserId(userId);
        task.setSourceText(dto.getText());
        task.setSpeed(dto.getSpeed());
        task.setNoiseType(dto.getNoiseType());
        task.setNoiseFactor(dto.getNoiseFactor() != null ? dto.getNoiseFactor() : BigDecimal.valueOf(AudioConstant.DEFAULT_NOISE_FACTOR));
        task.setStatus(AudioTaskLifecycle.initialStatus());
        audioTaskMapper.insert(task);
        log.info("Audio task created, taskId: {}, userId: {}", task.getId(), userId);
        transactionAfterCommitExecutor.execute(() -> audioTaskPublisher.publish(task));
        return new AudioTaskSubmitVO(task.getId(), AudioTaskLifecycle.initialStatus());
    }

    @Override
    public boolean callbackStart(AudioCallbackStartDTO dto) {
        int updated = audioTaskMapper.casUpdateStatus(dto.getTaskId(), AudioTaskLifecycle.callbackStartExpectedStatus(),
                AudioTaskLifecycle.callbackStartResultStatus(), null, null, null);
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
        if (!AudioTaskLifecycle.isAllowedFinishStatus(dto.getStatus()))
            throw new BaseException("无效的回调状态值: " + dto.getStatus());
        LocalDate completedDate = AudioTaskLifecycle.shouldSetCompletedDate(dto.getStatus()) ? LocalDate.now() : null;
        int updated = audioTaskMapper.casUpdateStatus(dto.getTaskId(), AudioTaskLifecycle.callbackFinishExpectedStatus(),
                dto.getStatus(), dto.getResultUrl(), dto.getErrorMsg(), completedDate);
        if (updated == 0) {
            log.warn("callbackFinish CAS failed, taskId: {} (already processed or not found)", dto.getTaskId());
            // 更新用户使用额度
            Long userId = audioTaskMapper.getUserIdByTaskId(dto.getTaskId());
            LocalDate today = LocalDate.now();
            // 原子递减（并发安全）
            if (userId != null) userQuotaApi.rollback(ConsumeQuotaCommand.dailyApiCall(userId, 1L, today));
            return false;
        }
        log.info("Audio task finished, taskId: {}, status: {}", dto.getTaskId(), dto.getStatus());
        return true;
    }

    @Override
    public AudioTaskVO getTask(Long taskId) {
        Long userId = BaseContext.getCurrentId();
        AudioTaskDO task = audioTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) throw new BaseException("任务不存在或无权访问");
        AudioTaskVO vo = new AudioTaskVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }

    @Override
    public PageResult listTasks(AudioTaskPageQueryDTO dto) {
        // 非管理员只能查询自己的任务，管理员可按 userId 筛选或查询全部
        if (!PermissionContext.isAdmin()) dto.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(dto.getPageNumOrDefault(), dto.getPageSizeOrDefault());
        List<AudioTaskDO> list = audioTaskMapper.selectByUserId(dto);
        Page<AudioTaskDO> page = (Page<AudioTaskDO>) list;
        List<AudioTaskVO> values = list.stream().map(task -> {
            AudioTaskVO vo = new AudioTaskVO();
            BeanUtils.copyProperties(task, vo);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult(page.getTotal(), values);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryFailedTask(Long taskId) {
        Long userId = BaseContext.getCurrentId();
        AudioTaskDO task = audioTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId))
            throw new BaseException("任务不存在或无权访问");

        int updated = audioTaskMapper.retryFailedTask(taskId, userId,
                AudioTaskLifecycle.Status.FAILED.code(), AudioTaskLifecycle.initialStatus());
        if (updated == 0)
            throw new BaseException("仅失败状态的任务可以重试");

        task.setStatus(AudioTaskLifecycle.initialStatus());
        task.setRetryTime(0);
        task.setResultUrl(null);
        task.setErrorMsg(null);
        task.setCompletedDate(null);
        transactionAfterCommitExecutor.execute(() -> audioTaskPublisher.publish(task));
        log.info("Audio task retry requested, taskId: {}, userId: {}", taskId, userId);
    }

    @Override
    public void requeueTask(AudioTaskDO task) {
        audioTaskPublisher.publish(task);
    }

    /**
     * 检查用户今日 API 调用次数。
     */
    private void checkDailyQuota(Long userId) {
        LocalDate today = LocalDate.now();
        ConsumeQuotaResult result = userQuotaApi.consume(ConsumeQuotaCommand.dailyApiCall(userId, 1L, today));
        if (!result.consumed())
            throw new RateLimitExceededException(String.format("今日 API 调用次数已达上限（%d 次），请明日再试", result.quota().limit()));
        // 原子递增（并发安全）
    }
}
