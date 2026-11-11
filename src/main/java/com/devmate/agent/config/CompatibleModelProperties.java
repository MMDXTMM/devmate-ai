package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

/** Administrator-defined endpoint allowlist; API users cannot provide arbitrary URLs. */
@Configuration
@ConfigurationProperties(prefix = "devmate.model-connections.compatible")
public class CompatibleModelProperties {
    private boolean enabled;
    private String displayName = "OpenAI 兼容／中转站";
    private String baseUrl;
    private List<String> models = List.of();

    public void validate() {
        if (!enabled) return;
        URI uri;
        try { uri = URI.create(baseUrl == null ? "" : baseUrl); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || displayName == null || displayName.isBlank() || displayName.length() > 100
                || models == null || models.isEmpty() || models.size() > 20
                || models.stream().anyMatch(model -> model == null || !model.matches("[A-Za-z0-9._:/-]{1,100}"))
                || models.stream().distinct().count() != models.size()) throw invalid();
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("兼容模型服务需配置无凭证的 HTTPS 地址、显示名称与不重复的模型白名单");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }
}
