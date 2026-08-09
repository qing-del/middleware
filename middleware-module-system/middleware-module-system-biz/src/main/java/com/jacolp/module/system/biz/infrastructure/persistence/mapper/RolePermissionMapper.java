package com.jacolp.module.system.biz.infrastructure.persistence.mapper;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper {

    List<RolePermissionDO> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    int insert(RolePermissionDO rolePermission);

    int deleteByRoleIdAndPermId(@Param("roleId") Long roleId, @Param("permId") Long permId);
}
