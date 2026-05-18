package com.jacolp.pojo.dto.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailResultDTO {
    private int successCount;
    private int failCount;
    private String message;
}
