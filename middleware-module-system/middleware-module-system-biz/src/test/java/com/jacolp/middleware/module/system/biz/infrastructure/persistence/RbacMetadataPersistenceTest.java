package com.jacolp.middleware.module.system.biz.infrastructure.persistence;

import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.PermissionDO;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.RolePermissionDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.PermissionMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.RolePermissionMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.repository.MyBatisPermissionMetadataRepository;
import com.jacolp.module.system.biz.infrastructure.persistence.repository.MyBatisRolePermissionMetadataRepository;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RbacMetadataPersistenceTest {

    @Test
    void mapperXmlDefinesTheRbacMetadataStatementsAndStableBatchOrdering() throws Exception {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/PermissionMapper.xml");
        parse(configuration, "mapper/RolePermissionMapper.xml");

        assertThat(configuration.hasStatement(
                "com.jacolp.module.system.biz.infrastructure.persistence.mapper.PermissionMapper.selectByCode")).isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.module.system.biz.infrastructure.persistence.mapper.PermissionMapper.selectActiveByRoleIds")).isTrue();
        assertThat(configuration.hasStatement(
                "com.jacolp.module.system.biz.infrastructure.persistence.mapper.RolePermissionMapper.selectByRoleIds")).isTrue();

        assertThat(content("mapper/PermissionMapper.xml"))
                .contains("p.status = 'active'")
                .contains("SELECT DISTINCT")
                .contains("ORDER BY p.code ASC, p.id ASC");
        assertThat(content("mapper/RolePermissionMapper.xml"))
                .contains("ORDER BY role_id ASC, perm_id ASC")
                .contains("#{roleId}")
                .contains("#{permId}");
    }

    @Test
    void permissionAdapterUsesOneSortedBatchQueryForDirectActiveRolePermissions() {
        PermissionMapper mapper = mock(PermissionMapper.class);
        MyBatisPermissionMetadataRepository repository = new MyBatisPermissionMetadataRepository(mapper);
        PermissionDO permission = new PermissionDO();
        permission.setCode("note:read");
        when(mapper.selectActiveByRoleIds(List.of(1L, 3L))).thenReturn(List.of(permission));

        assertThat(repository.findActiveByRoleIds(Arrays.asList(3L, null, 1L, 3L)))
                .containsExactly(permission);
        verify(mapper).selectActiveByRoleIds(List.of(1L, 3L));

        assertThat(repository.findActiveByRoleIds(List.of())).isEmpty();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void permissionAdapterDelegatesCatalogueQueriesAndWritesWithoutPermissionCalculation() {
        PermissionMapper mapper = mock(PermissionMapper.class);
        MyBatisPermissionMetadataRepository repository = new MyBatisPermissionMetadataRepository(mapper);
        PermissionDO permission = new PermissionDO();
        permission.setId(7L);
        permission.setCode("note:read");
        when(mapper.selectByCode("note:read")).thenReturn(permission);
        when(mapper.insert(permission)).thenReturn(1);
        when(mapper.updateById(permission)).thenReturn(1);
        when(mapper.deleteById(7L)).thenReturn(1);

        assertThat(repository.findByCode("note:read")).contains(permission);
        assertThat(repository.insert(permission)).isEqualTo(1);
        assertThat(repository.updateById(permission)).isEqualTo(1);
        assertThat(repository.deleteById(7L)).isEqualTo(1);
        verify(mapper).selectByCode("note:read");
        verify(mapper).insert(permission);
        verify(mapper).updateById(permission);
        verify(mapper).deleteById(7L);
    }

    @Test
    void rolePermissionAdapterUsesOneSortedBatchQueryAndDelegatesRelationWrites() {
        RolePermissionMapper mapper = mock(RolePermissionMapper.class);
        MyBatisRolePermissionMetadataRepository repository = new MyBatisRolePermissionMetadataRepository(mapper);
        RolePermissionDO rolePermission = new RolePermissionDO();
        rolePermission.setRoleId(2L);
        rolePermission.setPermId(3L);
        when(mapper.selectByRoleIds(List.of(1L, 2L))).thenReturn(List.of(rolePermission));
        when(mapper.insert(rolePermission)).thenReturn(1);
        when(mapper.deleteByRoleIdAndPermId(2L, 3L)).thenReturn(1);

        assertThat(repository.findByRoleIds(List.of(2L, 1L, 2L))).containsExactly(rolePermission);
        assertThat(repository.insert(rolePermission)).isEqualTo(1);
        assertThat(repository.deleteByRoleIdAndPermId(2L, 3L)).isEqualTo(1);
        verify(mapper).selectByRoleIds(List.of(1L, 2L));
        verify(mapper).insert(rolePermission);
        verify(mapper).deleteByRoleIdAndPermId(2L, 3L);
    }

    private static void parse(Configuration configuration, String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            new XMLMapperBuilder(inputStream, configuration, resource.getDescription(), configuration.getSqlFragments())
                    .parse();
        }
    }

    private static String content(String resourcePath) throws Exception {
        return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
    }
}
