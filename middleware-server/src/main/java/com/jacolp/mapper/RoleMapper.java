package com.jacolp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoleMapper {

    @Select("select COUNT(1) from sys_role where id = #{id}")
    Integer selectById(Long id);
}
