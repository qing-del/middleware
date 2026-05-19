package com.jacolp.mapper;

import com.jacolp.pojo.entity.ApiDailyUsageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ApiDailyUsageMapper {

    @Select("SELECT * FROM biz_api_daily_usage WHERE user_id = #{userId} AND record_date = #{date}")
    ApiDailyUsageEntity selectByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 原子递增当日用量，不存在则插入。
     * @return 更新后的 used_count
     */
    int incrementUsage(@Param("userId") Long userId, @Param("date") LocalDate date);
}
