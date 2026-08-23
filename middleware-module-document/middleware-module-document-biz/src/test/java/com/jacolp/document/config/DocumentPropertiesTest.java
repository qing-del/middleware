package com.jacolp.document.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DocumentPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DocumentModuleConfiguration.class);

    @Test
    void bindsDocumentRuntimeLimits() {
        contextRunner.withPropertyValues(
                "jacolp.document.enabled=true",
                "jacolp.document.websocket.max-update-bytes=1024",
                "jacolp.document.flush-log.batch-size=10",
                "jacolp.document.compact.max-unmerged-ops=20",
                "jacolp.document.snapshot.max-bytes=4096")
                .run(context -> {
                    DocumentProperties properties = context.getBean(DocumentProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getWebsocket().getMaxUpdateBytes()).isEqualTo(1024);
                    assertThat(properties.getFlushLog().getBatchSize()).isEqualTo(10);
                    assertThat(properties.getCompact().getMaxUnmergedOps()).isEqualTo(20);
                    assertThat(properties.getSnapshot().getMaxBytes()).isEqualTo(4096);
                });
    }
}
