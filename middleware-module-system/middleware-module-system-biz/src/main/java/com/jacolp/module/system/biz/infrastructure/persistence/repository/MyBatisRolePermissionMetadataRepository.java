package com.jacolp.module.system.biz.infrastructure.persistence.repository;

import com.jacolp.module.system.biz.application.port.out.RolePermissionMetadataRepository;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RolePermissionDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * MyBatis-backed implementation of direct role-permission metadata persistence.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisRolePermissionMetadataRepository implements RolePermissionMetadataRepository {

    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<RolePermissionDO> findByRoleIds(Collection<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        return normalizedRoleIds.isEmpty() ? List.of() : rolePermissionMapper.selectByRoleIds(normalizedRoleIds);
    }

    @Override
    public int insert(RolePermissionDO rolePermission) {
        return rolePermissionMapper.insert(rolePermission);
    }

    @Override
    public int deleteByRoleIdAndPermId(Long roleId, Long permId) {
        return rolePermissionMapper.deleteByRoleIdAndPermId(roleId, permId);
    }

    private static List<Long> normalizeRoleIds(Collection<Long> roleIds) {
        return roleIds == null ? List.of() : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
