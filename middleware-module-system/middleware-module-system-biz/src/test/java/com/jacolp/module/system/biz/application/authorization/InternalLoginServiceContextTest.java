package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.middleware.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.middleware.common.security.oauth2.token.Rs256AccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InternalLoginServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void missingPropertyDoesNotCreateServiceOrRequireSevenDependencies() {
        runner.run(context -> assertThat(context.getBeansOfType(InternalLoginService.class)).isEmpty());
    }

    @Test
    void falsePropertyDoesNotCreateServiceOrRequireSevenDependencies() {
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(InternalLoginService.class)).isEmpty());
    }

    @Test
    void enabledPropertyCreatesExactlyOneServiceWithSevenDependencies() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(InternalLoginService.class)).hasSize(1);
                    assertThat(context.getBeansOfType(InternalRegisteredClientPolicyResolver.class)).hasSize(1);
                    assertThat(context.getBeansOfType(InternalPasswordAccountAuthenticator.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EmailLoginCodeAuthenticator.class)).hasSize(1);
                    assertThat(context.getBeansOfType(EffectiveRolePermissionResolver.class)).hasSize(1);
                    assertThat(context.getBeansOfType(OAuth2ScopeResolver.class)).hasSize(1);
                    assertThat(context.getBeansOfType(Rs256AccessTokenIssuer.class)).hasSize(1);
                    assertThat(context.getBeansOfType(OAuth2RefreshTokenSessionService.class)).hasSize(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalLoginService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean
        InternalRegisteredClientPolicyResolver policyResolver() {
            return mock(InternalRegisteredClientPolicyResolver.class);
        }

        @Bean
        InternalPasswordAccountAuthenticator passwordAuthenticator() {
            return mock(InternalPasswordAccountAuthenticator.class);
        }

        @Bean
        EmailLoginCodeAuthenticator emailCodeAuthenticator() {
            return mock(EmailLoginCodeAuthenticator.class);
        }

        @Bean
        EffectiveRolePermissionResolver rolePermissionResolver() {
            return mock(EffectiveRolePermissionResolver.class);
        }

        @Bean
        OAuth2ScopeResolver scopeResolver() {
            return mock(OAuth2ScopeResolver.class);
        }

        @Bean
        Rs256AccessTokenIssuer accessTokenIssuer() {
            return mock(Rs256AccessTokenIssuer.class);
        }

        @Bean
        OAuth2RefreshTokenSessionService refreshTokenSessionService() {
            return mock(OAuth2RefreshTokenSessionService.class);
        }
    }
}
