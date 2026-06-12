package com.featureflag.sdk;

// FeatureFlagClient is the PUBLIC entry point of this SDK.
// It lives in com.featureflag.sdk (not in .internal) because it IS the API.
//
// The rule:
//   com.featureflag.sdk          → public API (this file, FlagEvaluationResult, SdkConfig)
//   com.featureflag.sdk.internal → implementation details (LocalFlagCache, FlagApiClient)
//
// SDK consumers should only ever import from com.featureflag.sdk.

import com.featureflag.sdk.internal.FlagApiClient;
import com.featureflag.sdk.internal.LocalFlagCache;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * Main entry point for the FeatureFlag SDK.
 *
 * <p>Create a single instance per application (treat it as a singleton)
 * and reuse it throughout. Creating multiple instances wastes connections
 * and bypasses the local cache.
 *
 * <p>Usage:
 * <pre>{@code
 * FeatureFlagClient client = FeatureFlagClient.builder()
 *     .serverUrl("https://your-featureflag-server.com")
 *     .apiKey("your-api-key")
 *     .localCacheTtl(Duration.ofSeconds(30))
 *     .build();
 *
 * boolean enabled = client.isEnabled("dark-mode", userId);
 *
 * // Always close when shutting down (releases the HTTP connection pool)
 * client.close();
 * }</pre>
 */
public class FeatureFlagClient implements Closeable {

    private static final Logger log = Logger.getLogger(FeatureFlagClient.class.getName());

    private static final String DEFAULT_ENVIRONMENT = "production";

    private final SdkConfig config;
    private final LocalFlagCache localCache;  // L1 cache — in-process, zero network
    private final FlagApiClient apiClient;    // L2+ — calls the server

    // Private constructor — force use of the builder pattern.
    // This ensures all required fields are validated before the client is usable.
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

    /**
     * Evaluate a boolean flag for the given user in the default (production) environment.
     * This is the most common usage — call it on every request without hesitation.
     * Hot-path latency is ~0.1ms when the L1 cache is warm.
     */
    public boolean isEnabled(String flagKey, String userId) {
        return evaluate(flagKey, userId, DEFAULT_ENVIRONMENT).enabled();
    }

    /**
     * Evaluate a flag with full result detail (reason, timing, cache status).
     * Use this when you need to know WHY a flag returned a particular value.
     */
    public FlagEvaluationResult evaluate(String flagKey, String userId) {
        return evaluate(flagKey, userId, DEFAULT_ENVIRONMENT);
    }

    /**
     * Evaluate a flag for a specific environment.
     * Use this when your application serves multiple environments.
     */
    public FlagEvaluationResult evaluate(String flagKey, String userId, String environment) {
        String cacheKey = buildCacheKey(flagKey, userId, environment);

        // L1: check the in-process LRU cache first (zero network)
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

        // L1 miss — call the server (which will hit L2 Redis or L3 PostgreSQL)
        log.fine(String.format("L1 cache MISS for flag '%s' — calling server", flagKey));
        FlagEvaluationResult serverResult = apiClient.evaluate(flagKey, userId, environment);

        // Populate L1 so subsequent calls within the TTL window are instant
        localCache.put(cacheKey, serverResult.enabled(), serverResult.reason());

        return serverResult;
    }

    /** Evict a specific entry from the L1 cache. */
    public void invalidate(String flagKey, String userId, String environment) {
        localCache.invalidate(buildCacheKey(flagKey, userId, environment));
    }

    /** Clear the entire L1 cache. Useful after bulk flag changes. */
    public void clearCache() {
        localCache.clear();
        log.info("Local flag cache cleared.");
    }

    /** Number of entries currently in the L1 cache. Useful for debugging. */
    public int cacheSize() {
        return localCache.size();
    }

    /**
     * Release resources. Call this when your application shuts down.
     * Closes the HTTP connection pool inside FlagApiClient.
     */
    @Override
    public void close() {
        localCache.clear();
        apiClient.shutdown();
        log.info("FeatureFlagClient closed.");
    }

    // ─────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────

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


    // Private helpers


    // Cache key includes flagKey + userId + environment so that the same flag
    // evaluated for different users or environments gets independent cache entries.
    private String buildCacheKey(String flagKey, String userId, String environment) {
        return flagKey + ":" + userId + ":" + environment;
    }
}
