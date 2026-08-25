package com.jacolp.framework.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Provides one application-scoped MinIO client without embedding any business bucket semantics. */
@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "jacolp.minio", name = "endpoint")
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {
    /** 创建共享 MinIO 客户端，并要求访问密钥成对出现。 */
    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(MinioProperties properties) {
        if (!properties.hasCompleteCredentials()) {
            throw new IllegalStateException("jacolp.minio.access-key and jacolp.minio.secret-key must both be configured");
        }
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /** 暴露逻辑桶名解析器，隔离业务配置键与物理桶名。 */
    @Bean
    @ConditionalOnMissingBean
    public MinioBucketResolver minioBucketResolver(MinioProperties properties) {
        return new DefaultMinioBucketResolver(properties);
    }

    /** 暴露字节导向对象存储门面，保留高级场景使用原始客户端的能力。 */
    @Bean
    @ConditionalOnMissingBean
    public MinioObjectStorage minioObjectStorage(MinioClient minioClient) {
        return new DefaultMinioObjectStorage(minioClient);
    }
}
