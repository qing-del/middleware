package com.jacolp.task;

import com.aliyun.oss.AliyunOSSClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "jacolp.aliyun.oss", name = "enabled", havingValue = "true")
public class AliyunOSSClientKeepLiveTask {
    @Autowired private AliyunOSSClient aliyunOSSClient;

    // 默认 45s 尝试保持一次活性
    @Scheduled(fixedRateString = "${jacolp.aliyun.oss.keep-live-time:45}", timeUnit = TimeUnit.SECONDS)
    public void keepLive() {
        log.debug("Aliyun OSS Client Keep Live Task");
        aliyunOSSClient.keepLive();
    }
}