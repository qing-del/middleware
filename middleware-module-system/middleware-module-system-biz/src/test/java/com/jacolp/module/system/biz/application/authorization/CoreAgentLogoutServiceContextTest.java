package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.middleware.common.security.context.CurrentAccessTokenAccessor;
import com.jacolp.middleware.common.security.oauth2.token.OAuth2SessionRevocationStore;
import com.jacolp.middleware.common.security.oauth2.token.OAuth2TokenStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentLogoutServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void missingOrFalsePropertyCreatesNoService() {
        runner.run(context -> assertThat(context.getBeansOfType(CoreAgentLogoutService.class)).isEmpty());
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentLogoutService.class)).isEmpty());
    }

    @Test
    void enabledPropertyCreatesOneService() {
        runner.withUserConfiguration(Dependencies.class).withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentLogoutService.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentLogoutService.class)
    static class Config {
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean CurrentAccessTokenAccessor access() { return mock(CurrentAccessTokenAccessor.class); }
        @Bean OAuth2TokenStateStore states() { return mock(OAuth2TokenStateStore.class); }
        @Bean OAuth2SessionRevocationStore revocations() { return mock(OAuth2SessionRevocationStore.class); }
    }
}
