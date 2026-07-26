package com.jacolp.audio.biz.persistence.mapper;

import com.jacolp.audio.biz.domain.dto.AudioTaskPageQueryDTO;
import com.jacolp.audio.biz.persistence.dataobject.AudioTaskDO;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioTaskMapper {
    int insert(AudioTaskDO task);
    AudioTaskDO selectById(Long id);
    List<AudioTaskDO> selectByUserId(@Param("dto") AudioTaskPageQueryDTO dto);
    /**
     * CAS 更新任务状态，WHERE status = expectedStatus 保证幂等性。
     * @return 影响行数，0 表示 CAS 失败（状态已变更）
     */
    int casUpdateStatus(@Param("id") Long id, @Param("expectedStatus") Integer expectedStatus, @Param("newStatus") Integer newStatus, @Param("resultUrl") String resultUrl, @Param("errorMsg") String errorMsg, @Param("completedDate") LocalDate completedDate);

    /**
     * 查询 PENDING 和 PROCESSING 状态的任务，且创建时间早于指定时间的任务
     * <p>最大限制返回 500 条数据</p>
     * @param before
     * @return
     */
    List<AudioTaskDO> selectPendingTimeout(@Param("before") LocalDateTime before);

    /**
     * 将任务的 retry_time +1，返回影响行数
     * @param id 音频任务主键 id
     * @param before 更新时间早于指定时间的任务才会被更新，避免重复更新
     * @return
     */
    int incrementRetryTime(@Param("id") Long id, @Param("before") LocalDateTime before);

    /**
     * 最大重试次数耗尽，将任务状态标记为 FAILED，并设置错误信息
     * @param id 音频任务主键 id
     * @param before 更新时间早于指定时间的任务才会被更新，避免重复更新
     * @param errorMsg 错误信息
     * @return
     */
    int markRetryExhausted(@Param("id") Long id, @Param("before") LocalDateTime before,
                           @Param("errorMsg") String errorMsg);
    @Select("SELECT user_id FROM audio_task WHERE id = #{taskId}") Long getUserIdByTaskId(@NotNull(message = "taskId 不能为空") Long taskId);
}
