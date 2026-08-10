package com.jacolp.module.system.biz.application.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2EmailLoginCodePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);
    @Test void defaultsAreTheMaximumAllowedPolicy() { runner.run(context -> { OAuth2EmailLoginCodeProperties p=context.getBean(OAuth2EmailLoginCodeProperties.class); assertThat(p.getCodeTtl()).isEqualTo(Duration.ofMinutes(10)); assertThat(p.getIssueCooldown()).isEqualTo(Duration.ofSeconds(60)); assertThat(p.getIssueWindow()).isEqualTo(Duration.ofHours(1)); assertThat(p.getMaxIssuesPerWindow()).isEqualTo(5); assertThat(p.getMaxFailedAttempts()).isEqualTo(5); }); }
    @Test void bindsStricterDurationsAndLimits() { runner.withPropertyValues("jacolp.oauth2.email-code.code-ttl=PT5M","jacolp.oauth2.email-code.issue-cooldown=PT2M","jacolp.oauth2.email-code.issue-window=PT2H","jacolp.oauth2.email-code.max-issues-per-window=3","jacolp.oauth2.email-code.max-failed-attempts=2").run(context -> assertThat(context).hasNotFailed()); }
    @Test void rejectsEveryWiderBoundary() { for(String property: new String[]{"code-ttl=PT0S","code-ttl=PT11M","issue-cooldown=PT59S","issue-window=PT59M","max-issues-per-window=0","max-issues-per-window=6","max-failed-attempts=0","max-failed-attempts=6"}) runner.withPropertyValues("jacolp.oauth2.email-code."+property).run(context -> assertThat(context).hasFailed()); }
    @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(OAuth2EmailLoginCodeProperties.class) static class Config {}
}
