package com.jacolp.mapper;

import com.jacolp.pojo.domain.UserQuoteStorageDO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("select * from sys_user where id = #{id}")
    UserEntity selectById(Long id);

    @Select("select * from sys_user where username = #{username}")
    UserEntity selectByUsername(String username);

    int upsertCreator(UserEntity user);

    int updateById(UserEntity user);

    int insertUser(UserEntity user);

    int deleteByIds(@Param("ids") List<Long> ids);

    List<UserEntity> selectByIds(@Param("ids") List<Long> ids);

    List<UserEntity> listByCondition(UserListDTO userListDTO);

    @Select("select role_id, max_storage_bytes, used_storage_bytes as usedStorageBytes from sys_user where id = #{id}")
    UserQuoteStorageDO selectQuoteStorageById(Long id);

    /**
     * 批量 <b>插入/更新</b> 用户
     * <p><b>不会修改密码</b></p>
     * @param users
     * @return
     */
    int upsertUser(List<UserEntity> users);
}
