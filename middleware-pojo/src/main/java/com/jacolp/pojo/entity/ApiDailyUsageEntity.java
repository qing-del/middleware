package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户每日 API 调用次数统计表 biz_api_daily_usage 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiDailyUsageEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private LocalDate recordDate;

    private Integer usedCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
