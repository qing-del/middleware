package com.jacolp.config;

import com.jacolp.interceptor.JwtTokenAdminInterceptor;
import com.jacolp.interceptor.JwtTokenUserInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer  {
    @Autowired private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    @Autowired private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 注册自定义拦截器
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("Start registering custom interceptors...");
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/user/login")
                .excludePathPatterns("/admin/user/register");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**");
    }

    /**
     * 创建API信息
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("个人SaaS中台项目")
                        .version("0.0.1")
                        .description("个人SaaS中台项目接口文档"));
    }

    /**
     * 创建 admin 部分的接口文档
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin 端接口")
                .packagesToScan("com.jacolp.controller.admin")
                .build();
    }


    /**
     * 设置静态资源映射
     * @param registry 资源处理器注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("Start setting up static resource mapping...");
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
