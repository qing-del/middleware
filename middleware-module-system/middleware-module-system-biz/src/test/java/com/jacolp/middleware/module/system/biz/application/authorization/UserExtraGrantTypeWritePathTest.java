package com.jacolp.middleware.module.system.biz.application.authorization;

import com.jacolp.constant.RoleConstant;
import com.jacolp.constant.UserConstant;
import com.jacolp.context.BaseContext;
import com.jacolp.middleware.common.security.token.TokenSessionService;
import com.jacolp.middleware.messaging.pulisher.UserProfileEventPublisher;
import com.jacolp.module.system.biz.application.authorization.UserGrantTypePolicy;
import com.jacolp.module.system.biz.application.dto.user.UserAddDTO;
import com.jacolp.module.system.biz.application.dto.user.UserModifyDTO;
import com.jacolp.module.system.biz.application.dto.user.UserRegisterDTO;
import com.jacolp.module.system.biz.application.service.EmailSenderService;
import com.jacolp.module.system.biz.application.service.impl.AdminUserServiceImpl;
import com.jacolp.module.system.biz.application.service.impl.UserUserServiceImpl;
import com.jacolp.module.system.biz.infrastructure.bootstrap.DataInitializer;
import com.jacolp.module.system.biz.infrastructure.persistence.dataobject.UserDO;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.RoleMapper;
import com.jacolp.module.system.biz.infrastructure.persistence.mapper.UserMapper;
import com.jacolp.module.system.biz.infrastructure.security.PasswordEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserGrantTypeWritePathTest {

    @AfterEach
    void clearContext() {
        BaseContext.remove();
    }

    @Test
    void fixedPolicyMapsOnlyTheThreeSupportedRoles() {
        assertThat(UserGrantTypePolicy.forRoleId(RoleConstant.USER))
                .isEqualTo("password,user_password,agent_client");
        assertThat(UserGrantTypePolicy.forRoleId(RoleConstant.ADMIN))
                .isEqualTo("admin_password,agent_client");
        assertThat(UserGrantTypePolicy.forRoleId(RoleConstant.CREATOR))
                .isEqualTo("admin_password,agent_client");
        assertThatThrownBy(() -> UserGrantTypePolicy.forRoleId(99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserGrantTypePolicy.forRoleId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userRegistrationWritesTheUserGrantSetExplicitly() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenSessionService tokenSessionService = mock(TokenSessionService.class);
        AtomicReference<UserDO> inserted = new AtomicReference<>();
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        doAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(7L);
            inserted.set(user);
            return 1;
        }).when(userMapper).insertUser(any(UserDO.class));
        when(userMapper.selectById(7L)).thenAnswer(invocation -> inserted.get());
        when(tokenSessionService.acquireActivationEmailCooldown(7L)).thenReturn(true);

        UserUserServiceImpl service = new UserUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "tokenSessionService", tokenSessionService);
        ReflectionTestUtils.setField(service, "emailSenderService", mock(EmailSenderService.class));
        ReflectionTestUtils.setField(service, "userProfileEvents", mock(UserProfileEventPublisher.class));
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("password");
        dto.setConfirmPassword("password");
        dto.setEmail("alice@example.com");

        service.register(dto);

        assertThat(inserted.get().getRoleId()).isEqualTo(RoleConstant.USER);
        assertThat(inserted.get().getGrantTypes()).isEqualTo("password,user_password,agent_client");
    }

    @Test
    void adminCreateAndRoleChangeWriteTheGrantSetForTheirTargetRole() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserProfileEventPublisher userProfileEvents = mock(UserProfileEventPublisher.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "userProfileEvents", userProfileEvents);
        BaseContext.setCurrentId(1L);
        UserDO creator = user(1L, RoleConstant.CREATOR);
        when(userMapper.selectById(1L)).thenReturn(creator);
        when(userMapper.selectByUsername("admin2")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        doAnswer(invocation -> {
            invocation.<UserDO>getArgument(0).setId(2L);
            return 1;
        }).when(userMapper).insertUser(any(UserDO.class));
        UserAddDTO add = new UserAddDTO();
        add.setUsername("admin2");
        add.setPassword("password");
        add.setRoleId(RoleConstant.ADMIN);
        add.setEmail("admin2@example.com");

        service.addUser(add);

        ArgumentCaptor<UserDO> inserted = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).insertUser(inserted.capture());
        assertThat(inserted.getValue().getGrantTypes()).isEqualTo("admin_password,agent_client");

        when(userMapper.updateById(any(UserDO.class))).thenReturn(1);
        when(userMapper.selectById(2L)).thenReturn(user(2L, RoleConstant.USER));
        UserModifyDTO modify = new UserModifyDTO();
        modify.setId(2L);
        modify.setRoleId(RoleConstant.USER);

        service.modifyUser(modify);

        ArgumentCaptor<UserDO> updated = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).updateById(updated.capture());
        assertThat(updated.getValue().getGrantTypes()).isEqualTo("password,user_password,agent_client");
    }

    @Test
    void creatorBootstrapWritesThePrivilegedGrantSetExplicitly() throws Exception {
        UserMapper userMapper = mock(UserMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(roleMapper.getAll()).thenReturn(List.of());
        when(userMapper.selectById(1L)).thenReturn(null);
        when(userMapper.selectByUsername("creator")).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("hash");
        when(userMapper.upsertCreator(any(UserDO.class))).thenReturn(1);
        DataInitializer initializer = new DataInitializer();
        ReflectionTestUtils.setField(initializer, "userMapper", userMapper);
        ReflectionTestUtils.setField(initializer, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(initializer, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(initializer, "adminUsername", "creator");
        ReflectionTestUtils.setField(initializer, "adminPassword", "password");
        ReflectionTestUtils.setField(initializer, "adminEmail", "creator@example.com");

        initializer.run();

        ArgumentCaptor<UserDO> creator = ArgumentCaptor.forClass(UserDO.class);
        verify(userMapper).upsertCreator(creator.capture());
        assertThat(creator.getValue().getRoleId()).isEqualTo(RoleConstant.CREATOR);
        assertThat(creator.getValue().getGrantTypes()).isEqualTo("admin_password,agent_client");
    }

    private static UserDO user(Long id, Long roleId) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRoleId(roleId);
        user.setStatus(UserConstant.ACTIVE_STATUS);
        return user;
    }
}
