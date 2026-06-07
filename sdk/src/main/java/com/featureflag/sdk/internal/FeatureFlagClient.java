package com.featureflag.sdk.internal;

import com.featureflag.sdk.FlagEvaluationResult;
import com.featureflag.sdk.SdkConfig;

import java.io.Closeable;
import java.util.logging.Logger;

public class FeatureFlagClient implements Closeable {

    private static final Logger log = Logger.getLogger(FeatureFlagClient.class.getName());


    private static final String DEFAULT_ENVIRONMENT = "production";

    private final SdkConfig config;
    private final LocalFlagCache localCache;  //
    private final FlagApiClient apiClient;

    private FeatureFlagClient(SdkConfig config) {
        this.config     = config;
        this.localCache = new LocalFlagCache(
                config.getLocalCacheMaxSize(),
                config.getLocalCacheTtl()
        );
        this.apiClient  = new FlagApiClient(config);
        log.info(String.format(
                "FeatureFlagClient initialized. Server: %s, CacheTtl: %s, CacheMaxSize: %d",
                config.getServerUrl(),
                config.getLocalCacheTtl(),
                config.getLocalCacheMaxSize()
        ));
    }

    public boolean isEnabled(String flagKey, String userId) {
        return evaluate(flagKey, userId, DEFAULT_ENVIRONMENT).enabled();
    }

    public FlagEvaluationResult evaluate(String flagKey, String userId) {
        return evaluate(flagKey, userId, DEFAULT_ENVIRONMENT);
    }

    public FlagEvaluationResult evaluate(String flagKey, String userId, String environment) {

        String cacheKey = buildCacheKey(flagKey, userId, environment);


        if (localCache.get(cacheKey).isPresent()) {

            FlagEvaluationResult cached = FlagEvaluationResult.fromCache(
                    flagKey,
                    localCache.isEnabled(cacheKey),
                    localCache.getReason(cacheKey),
                    localCache.getCachedAt(cacheKey)
            );
            log.fine(String.format(
                    "L1 cache HIT for flag '%s' userId '%s' → %b",
                    flagKey, userId, cached.enabled()));
            return cached;
        }


        log.fine(String.format("L1 cache MISS for flag '%s' — calling server", flagKey));

        FlagEvaluationResult serverResult = apiClient.evaluate(flagKey, userId, environment);


        localCache.put(cacheKey, serverResult.enabled(), serverResult.reason());

        return serverResult;
    }

    public void invalidate(String flagKey, String userId, String environment) {
        localCache.invalidate(buildCacheKey(flagKey, userId, environment));
    }

    public void clearCache() {
        localCache.clear();
        log.info("Local flag cache cleared.");
    }

    public int cacheSize() {
        return localCache.size();
    }

    @Override
    public void close() {
        localCache.clear();
        apiClient.shutdown();
        log.info("FeatureFlagClient closed.");
    }



    //builder
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final SdkConfig.Builder configBuilder = SdkConfig.builder();

        public Builder serverUrl(String serverUrl) {
            configBuilder.serverUrl(serverUrl);
            return this;
        }

        public Builder apiKey(String apiKey) {
            configBuilder.apiKey(apiKey);
            return this;
        }

        public Builder localCacheTtl(java.time.Duration ttl) {
            configBuilder.localCacheTtl(ttl);
            return this;
        }

        public Builder localCacheMaxSize(int maxSize) {
            configBuilder.localCacheMaxSize(maxSize);
            return this;
        }

        public Builder connectTimeout(java.time.Duration timeout) {
            configBuilder.connectTimeout(timeout);
            return this;
        }

        public Builder readTimeout(java.time.Duration timeout) {
            configBuilder.readTimeout(timeout);
            return this;
        }


        public FeatureFlagClient build() {
            return new FeatureFlagClient(configBuilder.build());
        }
    }


    //helper
    private String buildCacheKey(String flagKey, String userId, String environment) {
        return flagKey + ":" + userId + ":" + environment;
    }
}
