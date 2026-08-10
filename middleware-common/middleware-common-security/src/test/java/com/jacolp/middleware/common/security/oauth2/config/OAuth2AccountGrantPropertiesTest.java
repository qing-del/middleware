package com.jacolp.middleware.common.security.oauth2.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AccountGrantPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertiesConfiguration.class));

    @Test
    void defaultsAreTheRequiredImmutableAccountGrantSet() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            OAuth2AccountGrantProperties properties = context.getBean(OAuth2AccountGrantProperties.class);
            assertThat(properties.getDefaultGrantTypes())
                    .containsExactly("password", "email-code", "authorization_code");
            assertThat(properties.getDefaultGrantTypes()).isUnmodifiable();
        });
    }

    @Test
    void bindsCommaSeparatedDefaultsWithTrimNormalization() {
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.account.default-grant-types= password , email-code , authorization_code ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OAuth2AccountGrantProperties.class).getDefaultGrantTypes())
                            .containsExactly("password", "email-code", "authorization_code");
                });
    }

    @Test
    void bindsIndexedDefaultGrantList() {
        contextRunner.withPropertyValues(
                        "jacolp.oauth2.account.default-grant-types[0]=password",
                        "jacolp.oauth2.account.default-grant-types[1]=email-code",
                        "jacolp.oauth2.account.default-grant-types[2]=authorization_code")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OAuth2AccountGrantProperties.class).getDefaultGrantTypes())
                            .containsExactly("password", "email-code", "authorization_code");
                });
    }

    @Test
    void rejectsUnsafeOrIncompleteConfiguredDefaults() {
        contextRunner.withPropertyValues("jacolp.oauth2.account.default-grant-types=password,email-code")
                .run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("jacolp.oauth2.account.default-grant-types=password,email-code,refresh_token")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuth2AccountGrantProperties.class)
    static class PropertiesConfiguration {
    }
}
