package com.jacolp.task;

import com.jacolp.aspect.CheckAndUpdateUserStorageAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CleanAspectLockTask {
    @Autowired private CheckAndUpdateUserStorageAspect checkAndUpdateUserStorageAspect;

    /**
     * 定时清理 AOP 锁。
     * 默认每 60 分钟清理一次。
     */
    @Scheduled(fixedRateString = "${jacolp.aspect.lock.clean-aop-time:60}", timeUnit = TimeUnit.MINUTES)
    public void clean() {
        log.debug("Clean aspect lock map");
        checkAndUpdateUserStorageAspect.clearLockMap();
        log.debug("Clean aspect lock map done");
    }
}
