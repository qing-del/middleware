package com.jacolp.module.system.biz.application.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreAgentBrowserAuthenticationProviderContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderOnlyConfiguration.class);

    @Test
    void createsOneProviderRegardlessOfTheLegacyFlag() {
        runner.withUserConfiguration(DependencyConfiguration.class)
                .run(context -> assertThat(context.getBeansOfType(CoreAgentBrowserAuthenticationProvider.class)).hasSize(1));
        runner.withUserConfiguration(DependencyConfiguration.class).withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(CoreAgentBrowserAuthenticationProvider.class)).hasSize(1));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentBrowserAuthenticationProvider.class)
    static class ProviderOnlyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependencyConfiguration {
        @Bean
        CoreAgentBrowserAccountAuthenticator accountAuthenticator() {
            return mock(CoreAgentBrowserAccountAuthenticator.class);
        }
    }
}
