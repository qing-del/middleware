package com.jacolp;

import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.converter.MarkdownPublishService;
import com.jacolp.io.FileStorageService;
import com.jacolp.io.LocalMarkdownScanner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FlexmarkAutoconfigureApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MarkdownAutoConfiguration.class));

    @Test
    void autoConfigurationProvidesDefaultMarkdownComponents() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MarkdownProperty.class);
            assertThat(context).hasSingleBean(MarkdownHtmlEngine.class);
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context).hasSingleBean(MarkdownPublishService.class);
            assertThat(context).hasSingleBean(LocalMarkdownScanner.class);
        });
    }

}
