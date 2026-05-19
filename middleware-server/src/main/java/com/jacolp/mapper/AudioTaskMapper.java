package com.jacolp.mapper;

import com.jacolp.pojo.entity.AudioTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AudioTaskMapper {

    int insert(AudioTaskEntity task);

    AudioTaskEntity selectById(Long id);

    List<AudioTaskEntity> selectByUserId(@Param("userId") Long userId);

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
}
