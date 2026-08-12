package com.jacolp.module.system.biz.web.authorization;

import com.jacolp.middleware.common.core.metrics.QpsCounter;
import com.jacolp.middleware.common.security.jwt.JwtProperties;
import com.jacolp.module.system.biz.application.authorization.CoreAgentBrowserAccountAuthenticator;
import com.jacolp.module.system.biz.application.authorization.CoreAgentBrowserAuthenticationProvider;
import com.jacolp.module.system.biz.application.authorization.model.CoreAgentBrowserPrincipal;
import com.jacolp.module.system.biz.infrastructure.authorization.ActiveRegisteredClientRepository;
import com.jacolp.module.system.biz.infrastructure.authorization.CoreAgentAuthorizationServerConfiguration;
import com.jacolp.module.system.biz.infrastructure.authorization.FailClosedOAuth2AuthorizationService;
import com.jacolp.web.config.SecurityFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreAgentAuthorizationServerSecurityConfigurationTest {

    @Test
    void exactMatcherIncludesOnlyTheFivePhaseFourRoutes() {
        assertThat(matches("/oauth2/authorize")).isTrue();
        assertThat(matches("/oauth/token")).isTrue();
        assertThat(matches("/oauth/login")).isTrue();
        assertThat(matches("/oauth/consent")).isTrue();
        assertThat(matches("/oauth/logout")).isTrue();

        assertThat(matches("/oauth2/revoke")).isFalse();
        assertThat(matches("/oauth2/introspect")).isFalse();
        assertThat(matches("/.well-known/oauth-authorization-server")).isFalse();
        assertThat(matches("/oauth2/jwks")).isFalse();
        assertThat(matches("/oauth2/device_authorization")).isFalse();
        assertThat(matches("/oauth2/par")).isFalse();
        assertThat(matches("/user/protected")).isFalse();
    }

    @Test
    void sourceNeverUsesTheBroadSasEndpointMatcherOrAddsAuthorizationPersistence() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/jacolp/module/system/biz/web/authorization/"
                + "CoreAgentAuthorizationServerSecurityConfiguration.java"));

        assertThat(source).doesNotContain("getEndpointsMatcher", "OAuth2AuthorizationService", "OAuth2TokenGenerator",
                "InMemoryOAuth2AuthorizationService", "JdbcOAuth2AuthorizationService");
        assertThat(source).contains("securityMatcher(browserAndTokenRoutes)", "postPath(TOKEN_PATH)",
                "postPath(LOGOUT_PATH)", "browserAuthorizationCsrfFilter");
    }

    @Test
    void enabledContextUsesOrderOneAsChainAndOrderTwoFallbackWithBrowserCsrfProtection() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chains.getFilterChains()).hasSize(2);
            assertOrder(CoreAgentAuthorizationServerSecurityConfiguration.class,
                    "coreAgentAuthorizationServerSecurityFilterChain", 1);
            assertOrder(SecurityFilterConfiguration.class, "securityFilterChain", 2);
            assertThat(chains.getFilters("/oauth2/authorize")).anyMatch(CsrfFilter.class::isInstance);
            assertThat(chains.getFilters("/oauth2/revoke")).isNotEmpty();

            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            mvc.perform(post("/oauth2/authorize"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth2/authorize").param("_csrf", "wrong-token"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth/login"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/oauth/token").param("grant_type", "unsupported"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
            mvc.perform(post("/oauth/logout"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
            mvc.perform(get("/oauth2/revoke"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void unauthenticatedAuthorizeIsSavedThenValidProjectFormLoginReturnsToIt() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            CoreAgentBrowserAccountAuthenticator accountAuthenticator = context.getBean(CoreAgentBrowserAccountAuthenticator.class);
            when(accountAuthenticator.authenticate("alice", "password"))
                    .thenReturn(new CoreAgentBrowserPrincipal(7L, "alice", 3L, "USER", 1));
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            MockHttpSession session = new MockHttpSession();

            mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", "core_agent")
                            .session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/oauth/login"));

            CsrfTokenRepository csrfRepository = context.getBean(CsrfTokenRepository.class);
            MockHttpServletRequest csrfRequest = new MockHttpServletRequest("GET", "/oauth/login");
            csrfRequest.setSession(session);
            CsrfToken csrfToken = csrfRepository.generateToken(csrfRequest);
            csrfRepository.saveToken(csrfToken, csrfRequest, new MockHttpServletResponse());

            mvc.perform(post("/oauth/login").session(session).param("username", "alice")
                            .param("password", "password").param(csrfToken.getParameterName(), csrfToken.getToken()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/oauth2/authorize**"));
            verify(accountAuthenticator).authenticate("alice", "password");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void disabledContextLeavesOnlyTheLegacyFallbackAndDoesNotExposeCoreAgentRoutes() {
        try (AnnotationConfigWebApplicationContext context = disabledContext()) {
            FilterChainProxy chains = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chains.getFilterChains()).hasSize(1);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chains).build();
            mvc.perform(get("/oauth2/authorize")).andExpect(status().isNotFound());
            mvc.perform(get("/oauth/login")).andExpect(status().isNotFound());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean matches(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), path);
        return CoreAgentAuthorizationServerSecurityConfiguration.exactCoreAgentRoutes().matches(request);
    }

    private static void assertOrder(Class<?> type, String methodName, int expected) throws NoSuchMethodException {
        Method method = List.of(type.getDeclaredMethods()).stream().filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        assertThat(method.getAnnotation(Order.class)).isNotNull();
        assertThat(method.getAnnotation(Order.class).value()).isEqualTo(expected);
    }

    private static AnnotationConfigWebApplicationContext enabledContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("jacolp.oauth2.rs256.enabled", "true")));
        context.register(EnabledConfiguration.class);
        context.refresh();
        return context;
    }

    private static AnnotationConfigWebApplicationContext disabledContext() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("jacolp.oauth2.rs256.enabled", "false")));
        context.register(DisabledConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import({SecurityFilterConfiguration.class, CoreAgentAuthorizationServerConfiguration.class,
            CoreAgentAuthorizationServerSecurityConfiguration.class})
    static class EnabledConfiguration {
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey("test-user-secret");
            properties.setAdminSecretKey("test-admin-secret");
            properties.setActiveSecretKey("test-active-secret");
            return properties;
        }
        @Bean QpsCounter qpsCounter() { return mock(QpsCounter.class); }
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
        @Bean CoreAgentBrowserAccountAuthenticator browserAccountAuthenticator() { return mock(CoreAgentBrowserAccountAuthenticator.class); }
        @Bean CoreAgentBrowserAuthenticationProvider browserAuthenticationProvider(CoreAgentBrowserAccountAuthenticator authenticator) {
            return new CoreAgentBrowserAuthenticationProvider(authenticator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(SecurityFilterConfiguration.class)
    static class DisabledConfiguration {
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey("test-user-secret");
            properties.setAdminSecretKey("test-admin-secret");
            properties.setActiveSecretKey("test-active-secret");
            return properties;
        }
        @Bean QpsCounter qpsCounter() { return mock(QpsCounter.class); }
    }
}
