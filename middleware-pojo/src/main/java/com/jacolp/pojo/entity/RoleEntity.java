package com.jacolp.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统角色与额度配置表 sys_role 对应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String roleName;

    private String roleCode;

    private Integer dailyApiLimit;

    private Long maxStorageBytes;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
