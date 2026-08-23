package com.jacolp.framework.elasticsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings and logical index names shared by Elasticsearch consumers. */
@ConfigurationProperties(prefix = "jacolp.elasticsearch")
public class ElasticsearchProperties {
    private List<String> uris = new ArrayList<>();
    private String username;
    private String password;
    private Map<String, String> index = new LinkedHashMap<>();

    public List<String> getUris() { return uris; }
    public void setUris(List<String> uris) { this.uris = uris == null ? new ArrayList<>() : new ArrayList<>(uris); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Map<String, String> getIndex() { return index; }
    public void setIndex(Map<String, String> index) { this.index = index == null ? new LinkedHashMap<>() : new LinkedHashMap<>(index); }

    public boolean hasCompleteCredentials() {
        return isNotBlank(username) && isNotBlank(password);
    }

    public boolean hasPartialCredentials() {
        return isNotBlank(username) != isNotBlank(password);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
