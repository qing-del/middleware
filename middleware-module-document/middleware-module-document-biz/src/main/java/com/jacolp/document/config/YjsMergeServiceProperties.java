package com.jacolp.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 内部无状态 Yjs 合并服务的连接配置。 */
@ConfigurationProperties(prefix = "jacolp.yjs-merge-service")
public class YjsMergeServiceProperties {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String requireBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("jacolp.yjs-merge-service.base-url is required when document is enabled");
        }
        return baseUrl;
    }
}
