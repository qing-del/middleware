package com.jacolp.context;

import com.jacolp.middleware.common.security.context.AuthenticationContext;
import com.jacolp.middleware.common.security.context.AuthorizationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCompatibilityTest {

    @AfterEach
    void clearContexts() {
        BaseContext.remove();
        PermissionContext.remove();
    }

    @Test
    void newSecurityContextIsVisibleThroughLegacyFacade() {
        AuthenticationContext.setCurrentId(101L);
        AuthorizationContext.setAdmin(true);

        assertThat(BaseContext.getCurrentId()).isEqualTo(101L);
        assertThat(PermissionContext.isAdmin()).isTrue();
    }

    @Test
    void legacyFacadeWritesToNewSecurityContext() {
        BaseContext.setCurrentId(202L);
        PermissionContext.setAdmin(false);

        assertThat(AuthenticationContext.getCurrentId()).isEqualTo(202L);
        assertThat(AuthorizationContext.isAdmin()).isFalse();
    }
}
