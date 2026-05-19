package com.jacolp.pojo.vo.audio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "音频任务详情")
public class AudioTaskVO {

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "语速倍率")
    private BigDecimal speed;

    @Schema(description = "背景音类型")
    private String noiseType;

    @Schema(description = "背景音量因子")
    private BigDecimal noiseFactor;

    @Schema(description = "任务状态：0=排队中, 1=合成中, 2=已完成, -1=失败")
    private Integer status;

    @Schema(description = "成功后音频下载链接")
    private String resultUrl;

    @Schema(description = "失败时的错误信息")
    private String errorMsg;

    @Schema(description = "任务创建时间")
    private LocalDateTime createTime;

    @Schema(description = "任务完成日期")
    private LocalDate completedDate;
}
