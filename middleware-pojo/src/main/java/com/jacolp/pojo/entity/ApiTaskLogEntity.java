package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 异步任务明细表 biz_api_task_log 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiTaskLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String taskType;

    private Integer taskStatus;

    private String resultUrl;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
