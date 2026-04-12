package com.jacolp.task;

import com.aliyun.oss.AliyunOSSOperator;
import com.jacolp.constant.ImageConstant;
import com.jacolp.mapper.ImageDeleteDeadLetterMapper;
import com.jacolp.pojo.entity.ImageDeleteDeadLetterEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ImageDeleteTask {
    @Autowired private AliyunOSSOperator aliyunOSSOperator;
    @Autowired private ImageDeleteDeadLetterMapper imageDeleteDeadLetterMapper;

    /**
     * 定时任务，每 60 分钟执行一次。
     * 删除在死信队列中等待删除的图片
     */
    @Scheduled(fixedRateString = "${jacolp.image.delete-image-task-time:60}", timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void deleteImageTask() {
        log.debug("Start to delete images from OSS");
        List<ImageDeleteDeadLetterEntity> list = imageDeleteDeadLetterMapper.selectBatch(
                ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING
        );

        // 如果没有图片，则直接返回
        if (list == null || list.isEmpty()) {
            log.info("No images to delete");
            return;
        }

        ArrayList<Long> successList = new ArrayList<>();
        ArrayList<Long> failList = new ArrayList<>();

        list.forEach(imageDeleteDeadLetterEntity -> {
            String objectKey = extractObjectKeyFromUrl(imageDeleteDeadLetterEntity.getImageUrl());  // 获取 object key
            if (deleteImage(objectKey)) {
                successList.add(imageDeleteDeadLetterEntity.getId());
            } else {
                failList.add(imageDeleteDeadLetterEntity.getId());
            }
        });

        // 更新数据库
        if (!successList.isEmpty()) {
            int updateCount = imageDeleteDeadLetterMapper.updateBatch(
                    successList,    // 成功列表
                    ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED // 设置为完成状态
            );
            if (updateCount < successList.size()) {
                log.error("Update image delete dead letter status failed, count: {}", updateCount);
            }
        }

        if (!failList.isEmpty()) {
            int updateCount = imageDeleteDeadLetterMapper.updateBatch(
                    failList,       // 失败列表
                    ImageConstant.IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING   // 恢复为等待状态
            );
            if (updateCount < failList.size()) {
                log.error("Update image delete dead letter status failed, count: {}", updateCount);
            }
        }

        log.info("Delete images from OSS, success: {}, fail: {}", successList.size(), failList.size());
    }

    public boolean deleteImage(String ossUrl) {
        log.info("Delete image from OSS, ossUrl: {}", ossUrl);
        String objectKey = extractObjectKeyFromUrl(ossUrl);
        if (objectKey != null) {
            return aliyunOSSOperator.delete(objectKey);
        }
        return false;
    }

    /**
     * 从完整 URL 中提取 object key。
     */
    private String extractObjectKeyFromUrl(String ossUrl) {
        if (ossUrl == null || ossUrl.isEmpty()) {
            return null;
        }
        int index = ossUrl.indexOf(ImageConstant.IMAGE_OSS_DIRECTORY_PREFIX);
        if (index < 0 || index + 1 >= ossUrl.length()) {
            return null;
        }
        return ossUrl.substring(index);
    }
}
