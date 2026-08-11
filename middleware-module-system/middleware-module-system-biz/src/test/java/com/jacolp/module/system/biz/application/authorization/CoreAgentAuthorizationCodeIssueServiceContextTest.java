package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.middleware.common.security.oauth2.config.AccountGrantTypeResolver;
import com.jacolp.middleware.common.security.oauth2.token.SecureOAuth2TokenGenerator;
import com.jacolp.module.system.biz.application.port.out.AuthorizationAccountRepository;
import com.jacolp.module.system.biz.application.port.out.CoreAgentAuthorizationCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentAuthorizationCodeIssueServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceOnlyConfiguration.class);

    @Test
    void disabledOrMissingPropertyDoesNotCreateTheIssueServiceOrRequireItsDependencies() {
        runner.run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeIssueService.class)).isEmpty());
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeIssueService.class)).isEmpty());
    }

    @Test
    void enabledPropertyCreatesOneServiceWithAllRuntimeDependencies() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentAuthorizationCodeIssueService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationCodeIssueService.class)
    static class ServiceOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean SecureOAuth2TokenGenerator tokenGenerator() { return new SecureOAuth2TokenGenerator(); }
        @Bean CoreAgentRegisteredClientPolicyResolver policyResolver() { return mock(CoreAgentRegisteredClientPolicyResolver.class); }
        @Bean AuthorizationAccountRepository accountRepository() { return mock(AuthorizationAccountRepository.class); }
        @Bean AccountGrantTypeResolver accountGrantTypeResolver() {
            return new AccountGrantTypeResolver(AccountGrantTypeResolver.requiredDefaultGrantTypes());
        }
        @Bean EffectiveRolePermissionResolver rolePermissionResolver() { return mock(EffectiveRolePermissionResolver.class); }
        @Bean CoreAgentConsentScopeService consentScopeService() {
            return new CoreAgentConsentScopeService(new OAuth2ScopeResolver());
        }
        @Bean CoreAgentAuthorizationCodeStore authorizationCodeStore() { return mock(CoreAgentAuthorizationCodeStore.class); }
    }
}
