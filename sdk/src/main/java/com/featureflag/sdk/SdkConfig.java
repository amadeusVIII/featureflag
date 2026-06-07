package com.featureflag.sdk;

import java.time.Duration;


public final class SdkConfig {

    private final String serverUrl;
    private final String apiKey;
    private final Duration localCacheTtl;    // how long to keep results in L1 cache
    private final int localCacheMaxSize;     // maximum number of flag entries in L1 cache
    private final Duration connectTimeout;   // HTTP connection timeout
    private final Duration readTimeout;      // HTTP response read timeout


    private SdkConfig(Builder builder) {
        this.serverUrl       = builder.serverUrl;
        this.apiKey          = builder.apiKey;
        this.localCacheTtl   = builder.localCacheTtl;
        this.localCacheMaxSize = builder.localCacheMaxSize;
        this.connectTimeout  = builder.connectTimeout;
        this.readTimeout     = builder.readTimeout;
    }


    public String getServerUrl()          { return serverUrl; }
    public String getApiKey()             { return apiKey; }
    public Duration getLocalCacheTtl()    { return localCacheTtl; }
    public int getLocalCacheMaxSize()     { return localCacheMaxSize; }
    public Duration getConnectTimeout()   { return connectTimeout; }
    public Duration getReadTimeout()      { return readTimeout; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String serverUrl;
        private String apiKey;
        private Duration localCacheTtl    = Duration.ofSeconds(30);
        private int localCacheMaxSize     = 500;
        private Duration connectTimeout   = Duration.ofSeconds(2);
        private Duration readTimeout      = Duration.ofSeconds(3);

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder localCacheTtl(Duration ttl) {
            this.localCacheTtl = ttl;
            return this;
        }

        public Builder localCacheMaxSize(int maxSize) {
            this.localCacheMaxSize = maxSize;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public SdkConfig build() {
            // Validation — fail loudly at build time, not silently at runtime
            if (serverUrl == null || serverUrl.isBlank()) {
                throw new IllegalArgumentException("serverUrl is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey is required");
            }

            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            return new SdkConfig(this);
        }
    }
}