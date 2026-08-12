package com.jacolp.module.system.biz.application.authorization;

import com.jacolp.module.system.biz.application.port.out.CoreAgentAuthorizationCodeStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountAuthorizationStateRevocationServiceContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void missingOrFalsePropertyProvidesOnlyTheExplicitNoOp() {
        runner.run(context -> assertSingleImplementation(context,
                NoOpAccountAuthorizationStateRevocationService.class));
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false")
                .run(context -> assertSingleImplementation(context,
                        NoOpAccountAuthorizationStateRevocationService.class));
    }

    @Test
    void enabledPropertyProvidesOnlyTheRedisBackedImplementation() {
        runner.withUserConfiguration(EnabledDependencies.class).withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> assertSingleImplementation(context,
                        CoreAgentAccountAuthorizationStateRevocationService.class));
    }

    private static void assertSingleImplementation(org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
                                                   Class<? extends AccountAuthorizationStateRevocationService> type) {
        var services = context.getBeansOfType(AccountAuthorizationStateRevocationService.class);
        assertThat(services).hasSize(1);
        assertThat(services.values().iterator().next()).isInstanceOf(type);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({CoreAgentAccountAuthorizationStateRevocationService.class,
            NoOpAccountAuthorizationStateRevocationService.class})
    static class Config {
    }

    @Configuration(proxyBeanMethods = false)
    static class EnabledDependencies {
        @Bean CoreAgentAuthorizationCodeStore authorizationCodeStore() { return mock(CoreAgentAuthorizationCodeStore.class); }
    }
}
