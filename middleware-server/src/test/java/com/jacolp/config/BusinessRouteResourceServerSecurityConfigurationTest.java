package com.jacolp.config;

import com.jacolp.context.BaseContext;
import com.jacolp.context.PermissionContext;
import com.jacolp.middleware.common.security.oauth2.jwt.CoreNodeAccessTokenClaimsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessRouteResourceServerSecurityConfigurationTest {

    @Test
    void orderTwoMatchesOnlyCatalogueRoutesAndLeavesAllExceptionsAndOAuthAlone() throws Exception {
        Method chain = BusinessRouteResourceServerSecurityConfiguration.class.getDeclaredMethod(
                "businessRouteResourceServerSecurityFilterChain", org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                RequestMatcher.class, com.jacolp.middleware.common.security.oauth2.authorization.BusinessRouteAuthorizationPolicy.class,
                JwtDecoder.class, CoreNodeAccessTokenClaimsValidator.class);
        assertThat(AnnotationUtils.findAnnotation(chain, Order.class).value()).isEqualTo(2);

        RequestMatcher matcher = new BusinessRouteScopeCatalogConfiguration().businessRouteRequestMatcher();
        assertThat(matcher.matches(request(HttpMethod.GET, "/user/note/9"))).isTrue();
        assertThat(matcher.matches(request(HttpMethod.PUT, "/admin/user/user"))).isTrue();
        for (String exception : List.of("POST /user/user/login", "POST /user/user/logout", "POST /admin/user/login",
                "POST /admin/user/logout", "POST /user/user/register", "POST /user/user/resend-activation",
                "GET /user/user/active/token", "POST /user/user/active-code")) {
            String[] parts = exception.split(" ", 2);
            assertThat(matcher.matches(request(HttpMethod.valueOf(parts[0]), parts[1]))).isFalse();
        }
        assertThat(matcher.matches(request(HttpMethod.POST, "/oauth/token"))).isFalse();
    }

    @Test
    void resourceServerEnforcesScopesClientBoundaryClaimsAndCompatibilityContexts() throws Exception {
        try (AnnotationConfigWebApplicationContext context = enabledContext()) {
            FilterChainProxy chain = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
            assertThat(chain.getFilterChains()).hasSize(1);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();

            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-read"))
                    .andExpect(status().isOk()).andExpect(content().string("42:false"));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-wildcard"))
                    .andExpect(status().isOk()).andExpect(content().string("42:false"));
            mvc.perform(put("/admin/user/user").header("Authorization", "Bearer admin-creator"))
                    .andExpect(status().isOk()).andExpect(content().string("42:true"));

            mvc.perform(get("/user/note/9").header("Authorization", "Bearer core-agent"))
                    .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.containsString("无权访问")));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer user-no-scope"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/admin/user/user").header("Authorization", "Bearer user-manage"))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/user/note/9")).andExpect(status().isUnauthorized())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("认证失败")));
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/user/note/9").header("Authorization", "Bearer malformed"))
                    .andExpect(status().isUnauthorized());

            mvc.perform(post("/user/user/login")).andExpect(status().isOk()).andExpect(content().string("legacy"));
            mvc.perform(post("/oauth/token")).andExpect(status().isOk()).andExpect(content().string("oauth"));
        }
    }

    @Test
    void disabledPropertyDoesNotCreateTheOrderTwoBusinessChain() {
        try (AnnotationConfigWebApplicationContext context = context(false)) {
            assertThat(context.containsBean("businessRouteResourceServerSecurityFilterChain")).isFalse();
        }
    }

    private static AnnotationConfigWebApplicationContext enabledContext() {
        return context(true);
    }

    private static AnnotationConfigWebApplicationContext context(boolean enabled) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "jacolp.oauth2.rs256.enabled", Boolean.toString(enabled))));
        context.register(TestWebConfiguration.class, BusinessRouteScopeCatalogConfiguration.class,
                BusinessRouteResourceServerSecurityConfiguration.class);
        context.refresh();
        return context;
    }

    private static MockHttpServletRequest request(HttpMethod method, String path) {
        return new MockHttpServletRequest(method.name(), path);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestWebConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "invalid" -> throw new BadJwtException("invalid signature");
                case "malformed" -> jwt("user", "USER", List.of("note:read")).claim("roles", List.of("USER", "ADMIN")).build();
                case "user-read" -> jwt("user", "USER", List.of("note:read")).build();
                case "user-wildcard" -> jwt("user", "USER", List.of("*:read")).build();
                case "user-no-scope" -> jwt("user", "USER", List.of("media:read")).build();
                case "user-manage" -> jwt("user", "USER", List.of("*:manage")).build();
                case "admin-creator" -> jwt("admin", "CREATOR", List.of("account:manage")).build();
                case "core-agent" -> jwt("core_agent", "USER", List.of("note:read")).build();
                default -> throw new BadJwtException("unknown token");
            };
        }

        @Bean
        CoreNodeAccessTokenClaimsValidator coreNodeAccessTokenClaimsValidator() {
            return new CoreNodeAccessTokenClaimsValidator();
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/user/note/{id}")
        String userNote() {
            return BaseContext.getCurrentId() + ":" + PermissionContext.isAdmin();
        }

        @PutMapping("/admin/user/user")
        String adminUser() {
            return BaseContext.getCurrentId() + ":" + PermissionContext.isAdmin();
        }

        @PostMapping("/user/user/login")
        String legacy() {
            return "legacy";
        }

        @PostMapping("/oauth/token")
        String oauth() {
            return "oauth";
        }
    }

    private static Jwt.Builder jwt(String clientId, String role, List<String> scopes) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("42")
                .claim("username", "alice")
                .claim("client_id", clientId)
                .claim("grant_type", clientId.equals("core_agent") ? "authorization_code" : "password")
                .claim("roles", List.of(role))
                .claim("scope", scopes)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
    }
}
