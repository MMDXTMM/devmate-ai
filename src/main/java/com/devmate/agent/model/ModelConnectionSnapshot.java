package com.devmate.agent.model;

/**
 * Immutable runtime model configuration. The API key is deliberately excluded from toString().
 */
public final class ModelConnectionSnapshot {

    private final String provider;
    private final String model;
    private final String baseUrl;
    private final String apiKey;

    public ModelConnectionSnapshot(String provider, String model, String baseUrl, String apiKey) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String provider() { return provider; }

    public String model() { return model; }

    public String baseUrl() { return baseUrl; }

    public String apiKey() { return apiKey; }

    @Override
    public String toString() {
        return "ModelConnectionSnapshot[provider=" + provider + ", model=" + model + ", apiKey=REDACTED]";
    }
}
