package com.jacolp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class Rs256FinalModeGuardConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Rs256FinalModeGuardConfiguration.class);

    @Test
    void missingPropertyRejectsStartupBeforeApplicationBeansAreCreated() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(
                    "jacolp.oauth2.rs256.enabled=true");
        });
    }

    @Test
    void falsePropertyRejectsStartupBeforeApplicationBeansAreCreated() {
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(
                    "jacolp.oauth2.rs256.enabled=true");
        });
    }

    @Test
    void truePropertyAllowsTheMigrationContextToStart() {
        runner.withPropertyValues("jacolp.oauth2.rs256.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
        });
    }
}
