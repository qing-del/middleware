package com.jacolp.module.system.biz.application.port.out;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RolePermissionDO;

import java.util.Collection;
import java.util.List;

/**
 * Persistence port for direct entries in the role-to-permission relation.
 */
public interface RolePermissionMetadataRepository {

    List<RolePermissionDO> findByRoleIds(Collection<Long> roleIds);

    default List<RolePermissionDO> findByRoleId(Long roleId) {
        return roleId == null ? List.of() : findByRoleIds(List.of(roleId));
    }

    int insert(RolePermissionDO rolePermission);

    int deleteByRoleIdAndPermId(Long roleId, Long permId);
}
