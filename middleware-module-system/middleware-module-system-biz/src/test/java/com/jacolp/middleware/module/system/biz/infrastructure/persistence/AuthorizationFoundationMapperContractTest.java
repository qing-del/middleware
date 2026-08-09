package com.jacolp.middleware.module.system.biz.infrastructure.persistence;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RoleDO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationFoundationMapperContractTest {

    @Test
    void userGrantTypesAreMappedSafelyAcrossExistingReadAndWritePaths() throws Exception {
        assertThat(field(UserDO.class, "grantTypes").getType()).isEqualTo(String.class);

        Method byRoleId = UserMapper.class.getMethod("selectByRoleId", Integer.class);
        assertThat(byRoleId.getAnnotation(Select.class).value())
                .containsExactly("select id, username, nickname, email, role_id, grant_types, status from sys_user where role_id = #{roleId}");

        String mapperXml = new ClassPathResource("mapper/UserMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(mapperXml)
                .contains("INSERT INTO sys_user (id, username, password, email, role_id, grant_types, status")
                .contains("<if test=\"grantTypes != null and grantTypes != ''\">")
                .contains("u.grant_types")
                .contains("role_id, grant_types, status")
                .doesNotContain("INSERT INTO sys_user (username, password, email, role_id, grant_types")
                .doesNotContain("insert into sys_user (id, username, nickname, email, role_id, grant_types");
    }

    @Test
    void roleRankUsesTheExistingUnderscoreToCamelCaseSelectStarMapping() throws Exception {
        assertThat(field(RoleDO.class, "rank").getType()).isEqualTo(Integer.class);
        assertThat(RoleMapper.class.getMethod("getById", long.class).getAnnotation(Select.class).value())
                .containsExactly("select * from sys_role where id = #{id}");
        assertThat(RoleMapper.class.getMethod("getAll").getAnnotation(Select.class).value())
                .containsExactly("select * from sys_role");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
