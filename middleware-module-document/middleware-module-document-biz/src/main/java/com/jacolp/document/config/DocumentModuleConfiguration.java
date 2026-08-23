package com.jacolp.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers document module configuration without enabling any collaboration runtime prematurely. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DocumentProperties.class, YjsMergeServiceProperties.class})
public class DocumentModuleConfiguration {
}
