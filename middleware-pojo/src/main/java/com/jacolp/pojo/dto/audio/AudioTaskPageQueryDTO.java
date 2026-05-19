package com.jacolp.pojo.dto.audio;

import com.jacolp.pojo.provider.PageParamProvider;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioTaskPageQueryDTO implements PageParamProvider {
    @Positive(message = "用户ID不能小于0")
    private Long userId;

    @Positive(message = "页码不能小于0")
    private Integer pageNum;

    @Positive(message = "页大小不能小于0")
    private Integer pageSize;
}
