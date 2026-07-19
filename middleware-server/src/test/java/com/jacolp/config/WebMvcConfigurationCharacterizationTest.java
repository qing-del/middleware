package com.jacolp.config;

import com.jacolp.interceptor.JwtTokenActiveInterceptor;
import com.jacolp.interceptor.JwtTokenAdminInterceptor;
import com.jacolp.interceptor.JwtTokenUserInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebMvcConfigurationCharacterizationTest {

    @Test
    void preservesAuthenticationPathPatternsAndExclusions() {
        JwtTokenAdminInterceptor admin = mock(JwtTokenAdminInterceptor.class);
        JwtTokenUserInterceptor user = mock(JwtTokenUserInterceptor.class);
        JwtTokenActiveInterceptor active = mock(JwtTokenActiveInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration adminRegistration = registrationReturningItself();
        InterceptorRegistration userRegistration = registrationReturningItself();
        InterceptorRegistration activeRegistration = registrationReturningItself();

        when(registry.addInterceptor(admin)).thenReturn(adminRegistration);
        when(registry.addInterceptor(user)).thenReturn(userRegistration);
        when(registry.addInterceptor(active)).thenReturn(activeRegistration);

        WebMvcConfiguration configuration = new WebMvcConfiguration();
        ReflectionTestUtils.setField(configuration, "jwtTokenAdminInterceptor", admin);
        ReflectionTestUtils.setField(configuration, "jwtTokenUserInterceptor", user);
        ReflectionTestUtils.setField(configuration, "jwtTokenActiveInterceptor", active);

        configuration.addInterceptors(registry);

        verify(adminRegistration).addPathPatterns("/admin/**");
        verify(adminRegistration).excludePathPatterns("/admin/user/login");
        verify(adminRegistration).excludePathPatterns("/admin/audio/callback/**");
        verify(adminRegistration).excludePathPatterns(
                "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");

        verify(userRegistration).addPathPatterns("/user/**");
        verify(userRegistration).excludePathPatterns("/user/user/login");
        verify(userRegistration).excludePathPatterns("/user/user/register");
        verify(userRegistration).excludePathPatterns("/user/user/active-code");
        verify(userRegistration).excludePathPatterns("/user/user/resend-activation");
        verify(userRegistration).excludePathPatterns("/user/user/active/**");

        verify(activeRegistration).addPathPatterns("/user/user/active/**");
    }

    private static InterceptorRegistration registrationReturningItself() {
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registration.addPathPatterns(org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(registration);
        when(registration.excludePathPatterns(org.mockito.ArgumentMatchers.<String[]>any()))
                .thenReturn(registration);
        return registration;
    }
}
