package com.jacolp.pojo.vo.image;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User image statistics")
public class ImageOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Total image count of current user")
    private Long imageCount;

    @Schema(description = "Passed image count of current user")
    private Long passedCount;
}
