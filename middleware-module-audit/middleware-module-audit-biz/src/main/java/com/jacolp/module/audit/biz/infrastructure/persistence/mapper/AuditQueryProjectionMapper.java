package com.jacolp.module.audit.biz.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditQueryProjectionMapper {
    @Insert("INSERT INTO audit_query_subject_projection " +
            "(target_type, target_id, target_name, target_url, update_time) " +
            "VALUES (#{type}, #{id}, #{name}, #{url}, NOW()) " +
            "ON DUPLICATE KEY UPDATE target_name=VALUES(target_name), target_url=VALUES(target_url), update_time=NOW()")
    int upsertSubject(@Param("type") String type, @Param("id") long id,
                      @Param("name") String name, @Param("url") String url);

    @Insert("INSERT INTO audit_query_user_projection (user_id, username, nickname, update_time) " +
            "VALUES (#{id}, #{username}, #{nickname}, NOW()) " +
            "ON DUPLICATE KEY UPDATE username=VALUES(username), nickname=VALUES(nickname), update_time=NOW()")
    int upsertUser(@Param("id") long id, @Param("username") String username,
                   @Param("nickname") String nickname);
}
