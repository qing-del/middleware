package com.jacolp.pojo.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuoteStorageDO {
    private Long roleId;
    private Long maxStorageBytes;
    private Long usedStorageBytes;
}
