package com.jacolp.mapper;

import com.jacolp.pojo.entity.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {

    @Select("select COUNT(1) from sys_role where id = #{id}")
    Integer countById(Long id);

    @Select("select max_storage_bytes from sys_role where id = #{id}")
    Long selectMaxStorageById(Long id);

    @Select("select * from sys_role where id = #{id}")
    RoleEntity getById(long user);
}
