package com.jacolp.mapper;

import com.jacolp.pojo.dto.UserListDTO;
import com.jacolp.pojo.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("select * from sys_user where id = #{id}")
    UserEntity selectById(Long id);

    @Select("select * from sys_user where username = #{username}")
    UserEntity selectByUsername(String username);

    int insertCreator(UserEntity user);

    int updateById(UserEntity user);

    int insertUser(UserEntity user);

    List<UserEntity> listByCondition(UserListDTO userListDTO);
}
