package com.jacolp.framework.minio;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings and logical bucket names shared by modules using MinIO. */
@ConfigurationProperties(prefix = "jacolp.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private Map<String, String> bucket = new LinkedHashMap<>();

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public Map<String, String> getBucket() { return bucket; }
    public void setBucket(Map<String, String> bucket) { this.bucket = bucket == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bucket); }

    public boolean hasCompleteCredentials() {
        return isNotBlank(accessKey) && isNotBlank(secretKey);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
