package com.featureflag.sdk.internal;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


public class LocalFlagCache {

    private static class CacheEntry {
        final boolean enabled;
        final String reason;
        final long cachedAtMs;   // System.currentTimeMillis() when entry was stored

        CacheEntry(boolean enabled, String reason, long cachedAtMs) {
            this.enabled    = enabled;
            this.reason     = reason;
            this.cachedAtMs = cachedAtMs;
        }

        Instant cachedAt() {
            return Instant.ofEpochMilli(cachedAtMs);
        }
    }

    private final Map<String, CacheEntry> store;
    private final long ttlMillis;


    public LocalFlagCache(int maxSize, java.time.Duration ttl) {
        this.ttlMillis = ttl.toMillis();

        this.store = Collections.synchronizedMap(
                new LinkedHashMap<String, CacheEntry>(
                        maxSize,
                        0.75f,
                        true
                ) {

                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                        return size() > maxSize;  // evict when we exceed the limit
                    }
                }
        );

    }


    public Optional<CacheEntry> get(String cacheKey) {
        synchronized (store) {
            CacheEntry entry = store.get(cacheKey);

            if (entry == null) {
                return Optional.empty();
            }


            long ageMs = System.currentTimeMillis() - entry.cachedAtMs;
            if (ageMs > ttlMillis) {
                store.remove(cacheKey);
                return Optional.empty();
            }

            return Optional.of(entry);
        }
    }


    public void put(String cacheKey, boolean enabled, String reason) {
        store.put(cacheKey, new CacheEntry(enabled, reason, System.currentTimeMillis()));
    }

    public void invalidate(String cacheKey) {
        store.remove(cacheKey);
    }

    public void clear() {
        store.clear();
    }

    //for tests and the client layer
    CacheEntry getEntry(String cacheKey) {
        synchronized (store) {
            return store.get(cacheKey);
        }
    }

    public boolean isEnabled(String cacheKey) {
        CacheEntry e = getEntry(cacheKey);
        return e != null && e.enabled;
    }


    public String getReason(String cacheKey) {
        CacheEntry e = getEntry(cacheKey);
        return e != null ? e.reason : "UNKNOWN";
    }

    public Instant getCachedAt(String cacheKey) {
        CacheEntry e = getEntry(cacheKey);
        return e != null ? e.cachedAt() : Instant.now();
    }


    public int size() {
        return store.size();
    }

}
