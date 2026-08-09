package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.PermissionDO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the permission catalogue and direct, active role grants.
 */
public interface PermissionMetadataRepository {

    Optional<PermissionDO> findByCode(String code);

    List<PermissionDO> findActiveByRoleIds(Collection<Long> roleIds);

    default List<PermissionDO> findActiveByRoleId(Long roleId) {
        return roleId == null ? List.of() : findActiveByRoleIds(List.of(roleId));
    }

    int insert(PermissionDO permission);

    int updateById(PermissionDO permission);

    int deleteById(Long id);
}
