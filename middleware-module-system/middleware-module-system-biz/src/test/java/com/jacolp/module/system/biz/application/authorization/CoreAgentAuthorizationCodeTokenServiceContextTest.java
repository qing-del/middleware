package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.middleware.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.middleware.common.security.oauth2.token.OAuth2RefreshTokenSessionService;
import com.jacolp.middleware.common.security.oauth2.token.Rs256AccessTokenIssuer;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentAuthorizationCodeTokenServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void disabledOrMissingPropertyDoesNotCreateTokenServiceOrRequireDependencies() {
        runner.run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeTokenService.class)).isEmpty());
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeTokenService.class)).isEmpty());
    }

    @Test
    void enabledPropertyCreatesExactlyOneTokenService() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeTokenService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeTokenService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean AccountGrantTypeResolver accountGrantTypeResolver() { return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes()); }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return mock(EffectiveRolePermissionResolver.class); }
        @Bean OAuth2ScopeResolver scopeResolver() { return mock(OAuth2ScopeResolver.class); }
        @Bean Rs256AccessTokenIssuer accessTokenIssuer() { return mock(Rs256AccessTokenIssuer.class); }
        @Bean OAuth2RefreshTokenSessionService refreshTokenSessionService() { return mock(OAuth2RefreshTokenSessionService.class); }
    }
}
