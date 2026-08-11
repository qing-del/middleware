package com.jacolp.module.system.biz.infrastructure.authorization;

import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeRequestAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationCodeTokenAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationConsentAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentAuthorizationEndpointAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentPublicClientAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentPublicClientAuthenticationProvider;
import com.jacolp.module.system.biz.web.authorization.CoreAgentRefreshTokenAuthenticationConverter;
import com.jacolp.module.system.biz.web.authorization.CoreAgentRefreshTokenAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CoreAgentAuthorizationServerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationUnderTest.class);

    @Test
    void settingsFixOnlyAuthorizationTokenAndConsentPathsWithoutForgingAnIssuerOrLogoutEndpoint() {
        AuthorizationServerSettings settings = new CoreAgentAuthorizationServerConfiguration()
                .coreAgentAuthorizationServerSettings();

        assertThat(settings.getAuthorizationEndpoint()).isEqualTo("/oauth2/authorize");
        assertThat(settings.getTokenEndpoint()).isEqualTo("/oauth/token");
        assertThat(settings.getTokenRevocationEndpoint()).isNotEqualTo("/oauth/logout");
        assertThat(settings.getIssuer()).isNull();
        assertThat(CoreAgentAuthorizationServerConfiguration.CONSENT_PAGE).isEqualTo("/oauth/consent");
    }

    @Test
    void replacementMethodsRemoveEveryDefaultAndKeepOnlyTheSpecifiedComponentsInOrder() {
        AuthenticationConverter defaultConverter = request -> null;
        AuthenticationConverter firstConverter = request -> null;
        AuthenticationConverter secondConverter = request -> null;
        AuthenticationProvider defaultProvider = provider();
        AuthenticationProvider firstProvider = provider();
        AuthenticationProvider secondProvider = provider();

        List<AuthenticationConverter> clientConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceClientAuthenticationConverters(clientConverters,
                List.of(firstConverter));
        assertThat(clientConverters).containsExactly(firstConverter);

        List<AuthenticationProvider> clientProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceClientAuthenticationProviders(clientProviders,
                List.of(firstProvider));
        assertThat(clientProviders).containsExactly(firstProvider);

        List<AuthenticationConverter> authorizationConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceAuthorizationEndpointConverters(authorizationConverters,
                List.of(firstConverter));
        assertThat(authorizationConverters).containsExactly(firstConverter);

        List<AuthenticationProvider> authorizationProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceAuthorizationEndpointProviders(authorizationProviders,
                List.of(firstProvider, secondProvider));
        assertThat(authorizationProviders).containsExactly(firstProvider, secondProvider);

        List<AuthenticationConverter> tokenConverters = new ArrayList<>(List.of(defaultConverter));
        CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointConverters(tokenConverters,
                List.of(firstConverter, secondConverter));
        assertThat(tokenConverters).containsExactly(firstConverter, secondConverter);

        List<AuthenticationProvider> tokenProviders = new ArrayList<>(List.of(defaultProvider));
        CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointProviders(tokenProviders,
                List.of(firstProvider, secondProvider));
        assertThat(tokenProviders).containsExactly(firstProvider, secondProvider);

        assertThatThrownBy(() -> CoreAgentAuthorizationServerConfigurerFactory.replaceTokenEndpointConverters(
                new ArrayList<>(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configurationIsConditionalAndProvidesOnlySettingsAndTheUnappliedFactory() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).isEmpty();
            assertThat(context.getBeansOfType(CoreAgentAuthorizationServerConfigurerFactory.class)).isEmpty();
        });
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false").run(context -> {
            assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).isEmpty();
            assertThat(context.getBeansOfType(CoreAgentAuthorizationServerConfigurerFactory.class)).isEmpty();
        });
        runner.withUserConfiguration(DependenciesConfiguration.class)
                .withPropertyValues("jacolp.oauth2.rs256.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(AuthorizationServerSettings.class)).hasSize(1);
                    assertThat(context.getBeansOfType(CoreAgentAuthorizationServerConfigurerFactory.class)).hasSize(1);
                });
    }

    @Test
    void wiringDoesNotCreateSasAuthorizationPersistenceOrDefaultProtocolComponents() throws IOException {
        String configuration = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/infrastructure/authorization/"
                + "CoreAgentAuthorizationServerConfiguration.java"));
        String factory = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/infrastructure/authorization/"
                + "CoreAgentAuthorizationServerConfigurerFactory.java"));

        assertThat(configuration + factory).doesNotContain("InMemoryOAuth2AuthorizationService",
                "JdbcOAuth2AuthorizationService", "OAuth2TokenGenerator", ".apply(",
                "deviceAuthorizationEndpoint", "pushedAuthorizationRequestEndpoint", "oidc(");
        assertThat(factory).contains("authorizationService(authorizationService)",
                "replaceClientAuthenticationConverters", "replaceAuthorizationEndpointConverters",
                "replaceTokenEndpointConverters");
    }

    private static AuthenticationProvider provider() {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                return null;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return false;
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CoreAgentAuthorizationServerConfiguration.class)
    static class ConfigurationUnderTest {
    }

    @Configuration(proxyBeanMethods = false)
    static class DependenciesConfiguration {
        @Bean ActiveRegisteredClientRepository registeredClientRepository() { return mock(ActiveRegisteredClientRepository.class); }
        @Bean FailClosedOAuth2AuthorizationService authorizationService() { return mock(FailClosedOAuth2AuthorizationService.class); }
        @Bean OAuth2AuthorizationConsentService authorizationConsentService() { return mock(OAuth2AuthorizationConsentService.class); }
        @Bean CoreAgentPublicClientAuthenticationConverter publicClientAuthenticationConverter() { return mock(CoreAgentPublicClientAuthenticationConverter.class); }
        @Bean CoreAgentPublicClientAuthenticationProvider publicClientAuthenticationProvider() { return mock(CoreAgentPublicClientAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationEndpointAuthenticationConverter authorizationEndpointAuthenticationConverter() { return mock(CoreAgentAuthorizationEndpointAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeRequestAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationConsentAuthenticationProvider authorizationConsentAuthenticationProvider() { return mock(CoreAgentAuthorizationConsentAuthenticationProvider.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationConverter authorizationCodeTokenAuthenticationConverter() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationConverter.class); }
        @Bean CoreAgentRefreshTokenAuthenticationConverter refreshTokenAuthenticationConverter() { return mock(CoreAgentRefreshTokenAuthenticationConverter.class); }
        @Bean CoreAgentAuthorizationCodeTokenAuthenticationProvider authorizationCodeTokenAuthenticationProvider() { return mock(CoreAgentAuthorizationCodeTokenAuthenticationProvider.class); }
        @Bean CoreAgentRefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider() { return mock(CoreAgentRefreshTokenAuthenticationProvider.class); }
    }
}
