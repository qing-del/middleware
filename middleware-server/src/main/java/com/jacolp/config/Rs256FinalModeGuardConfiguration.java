package com.jacolp.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Temporary Phase 6 migration guard.
 *
 * <p>It runs before singleton creation so a partially migrated deployment cannot select the old
 * HS256 path while individual RS256 conditional components are being made unconditional. The
 * property and this guard are removed together once that cleanup is complete.</p>
 */
@Configuration(proxyBeanMethods = false)
public class Rs256FinalModeGuardConfiguration {

    static final String RS256_ENABLED_PROPERTY = "jacolp.oauth2.rs256.enabled";

    @Bean
    static BeanFactoryPostProcessor requireRs256DuringPhaseSixMigration(Environment environment) {
        return beanFactory -> {
            if (!environment.getProperty(RS256_ENABLED_PROPERTY, Boolean.class, false)) {
                throw new IllegalStateException("Phase 6 requires jacolp.oauth2.rs256.enabled=true; "
                        + "configure valid external RSA PEM material before deployment");
            }
        };
    }
}
