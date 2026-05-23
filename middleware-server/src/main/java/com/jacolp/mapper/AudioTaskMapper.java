package com.jacolp.mapper;

import com.jacolp.pojo.dto.audio.AudioTaskPageQueryDTO;
import com.jacolp.pojo.entity.AudioTaskEntity;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AudioTaskMapper {

    int insert(AudioTaskEntity task);

    AudioTaskEntity selectById(Long id);

    List<AudioTaskEntity> selectByUserId(@Param("dto") AudioTaskPageQueryDTO dto);

    /**
     * CAS 更新任务状态，WHERE status = expectedStatus 保证幂等性。
     * @return 影响行数，0 表示 CAS 失败（状态已变更）
     */
    int casUpdateStatus(@Param("id") Long id,
                        @Param("expectedStatus") Integer expectedStatus,
                        @Param("newStatus") Integer newStatus,
                        @Param("resultUrl") String resultUrl,
                        @Param("errorMsg") String errorMsg,
                        @Param("completedDate") LocalDate completedDate);

    List<AudioTaskEntity> selectPendingTimeout(@Param("before") LocalDateTime before);

    @Select("SELECT user_id FROM audio_task WHERE id = #{taskId}")
    Long getUserIdByTaskId(@NotNull(message = "taskId 不能为空") Long taskId);
}
