package com.jacolp.task;

import com.jacolp.constant.AudioConstant;
import com.jacolp.mapper.AudioTaskMapper;
import com.jacolp.pojo.entity.AudioTaskEntity;
import com.jacolp.service.AudioTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AudioTaskRetryTask {

    @Autowired private AudioTaskMapper audioTaskMapper;
    @Autowired private AudioTaskService audioTaskService;

    /**
     * 每 5 分钟扫描一次，将卡在 PENDING 超过 10 分钟的任务重新入队。
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void retryPendingTasks() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(AudioConstant.TASK_TIMEOUT_MINUTES);
        List<AudioTaskEntity> stuckTasks = audioTaskMapper.selectPendingTimeout(timeout);

        if (stuckTasks == null || stuckTasks.isEmpty()) {
            log.debug("No stuck audio tasks found");
            return;
        }

        log.info("Found {} stuck audio tasks, re-queuing...", stuckTasks.size());
        for (AudioTaskEntity task : stuckTasks) {
            try {
                audioTaskService.requeueTask(task);
                log.info("Re-queued stuck audio task, taskId: {}", task.getId());
            } catch (Exception e) {
                log.error("Failed to re-queue audio task, taskId: {}, error: {}", task.getId(), e.getMessage());
            }
        }
    }
}
