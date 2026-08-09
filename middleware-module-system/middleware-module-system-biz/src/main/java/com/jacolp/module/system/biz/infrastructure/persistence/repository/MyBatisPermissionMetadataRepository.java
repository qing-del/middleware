package com.jacolp.module.system.biz.infrastructure.persistence.repository;

import com.jacolp.module.system.biz.application.port.out.PermissionMetadataRepository;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.PermissionDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed implementation of permission metadata persistence.
 */
@Repository
@RequiredArgsConstructor
public class MyBatisPermissionMetadataRepository implements PermissionMetadataRepository {

    private final PermissionMapper permissionMapper;

    @Override
    public Optional<PermissionDO> findByCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(permissionMapper.selectByCode(code));
    }

    @Override
    public List<PermissionDO> findActiveByRoleIds(Collection<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeRoleIds(roleIds);
        return normalizedRoleIds.isEmpty() ? List.of() : permissionMapper.selectActiveByRoleIds(normalizedRoleIds);
    }

    @Override
    public int insert(PermissionDO permission) {
        return permissionMapper.insert(permission);
    }

    @Override
    public int updateById(PermissionDO permission) {
        return permissionMapper.updateById(permission);
    }

    @Override
    public int deleteById(Long id) {
        return permissionMapper.deleteById(id);
    }

    private static List<Long> normalizeRoleIds(Collection<Long> roleIds) {
        return roleIds == null ? List.of() : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
