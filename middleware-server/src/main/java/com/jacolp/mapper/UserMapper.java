package com.jacolp.mapper;

import com.jacolp.pojo.dto.user.UserQuoteStorageDTO;
import com.jacolp.pojo.dto.user.UserListDTO;
import com.jacolp.pojo.dto.user.UserStorageHandlerDTO;
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

    @Select("select * from sys_user where email = #{email}")
    UserEntity selectByEmail(String email);

    @Select("select id, username, nickname, email, role_id, status from sys_user where role_id = #{roleId}")
    List<UserEntity> selectByRoleId(Integer roleId);

    int upsertCreator(UserEntity user);

    int updateById(UserEntity user);

    int insertUser(UserEntity user);

    int deleteByIds(@Param("ids") List<Long> ids);

    List<UserEntity> selectByIds(@Param("ids") List<Long> ids);

    List<UserEntity> listByCondition(UserListDTO userListDTO);

    @Select("select role_id, max_storage_bytes, used_storage_bytes as usedStorageBytes from sys_user where id = #{id}")
    UserQuoteStorageDTO selectQuoteStorageById(Long id);

    /**
     * 批量查询用户存储信息
     * @param userIds 用户ID列表
     * @return 用户存储信息列表
     */
    List<UserQuoteStorageDTO> selectQuoteStorageByIds(@Param("ids") List<Long> userIds);

    /**
     * 批量更新用户存储（仅更新 used_storage_bytes）
     * <p>- 建议批量<b>减少</b>{@code used_storage_bytes}使用，增加不建议使用</p>
     * @param users 用户列表
     * @return 更新数量
     */
    int batchUpdateStorage(@Param("users") List<UserStorageHandlerDTO> users);

    /**
     * 批量 <b>插入/更新</b> 用户
     * <p><b>不会修改密码</b></p>
     * @param users
     * @return
     */
    int upsertUser(List<UserEntity> users);

    /**
     * 批量更新用户存储（仅更新 used_storage_bytes）
     * <p>- 采用增量的方式，带有 CAS 特性， 如果 CAS 失败会返回 0</p>
     * @param updateUser 用户（使用增量的方式）
     * @return 更新数量
     */
    int updateStorageById(@Param("updateUser") UserStorageHandlerDTO updateUser);

    /**
     * 批量更新用户最大存储（仅更新 max_storage_bytes）
     * @param id 用户ID
     * @param maxStorageBytes 最大存储字节数
     * @return 更新数量
     */
    int updateMaxStorageById(Long id, Long maxStorageBytes);

    /**
     * 批量查询用户数量
     * @param userIds 用户ID列表
     * @return 用户数量
     */
    int countByIds(List<Long> userIds);
}
