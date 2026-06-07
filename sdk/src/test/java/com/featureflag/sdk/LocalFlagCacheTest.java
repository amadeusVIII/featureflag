package com.featureflag.sdk;

import com.featureflag.sdk.internal.LocalFlagCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFlagCacheTest {

    private LocalFlagCache cache;

    @BeforeEach
    void setUp() {
        // 3 max entries, 100ms TTL — small enough to test expiry and eviction fast
        cache = new LocalFlagCache(3, Duration.ofMillis(100));
    }

    @Test
    @DisplayName("get() returns empty for a key that was never stored")
    void get_unknownKey_returnsEmpty() {
        assertThat(cache.get("nonexistent:user-1:production")).isEmpty();
    }

    @Test
    @DisplayName("put() then get() returns the stored value")
    void putAndGet_returnsStoredValue() {
        cache.put("dark-mode:user-1:production", true, "ROLLOUT");

        Optional<?> result = cache.get("dark-mode:user-1:production");

        assertThat(result).isPresent();
        assertThat(cache.isEnabled("dark-mode:user-1:production")).isTrue();
        assertThat(cache.getReason("dark-mode:user-1:production")).isEqualTo("ROLLOUT");
    }

    @Test
    @DisplayName("get() returns empty after TTL expires")
    void get_afterTtlExpiry_returnsEmpty() throws InterruptedException {
        cache.put("expired-flag:user-1:production", true, "ROLLOUT");

        // Confirm it's there before expiry
        assertThat(cache.get("expired-flag:user-1:production")).isPresent();

        // Wait for TTL to pass (cache has 100ms TTL, wait 150ms to be safe)
        Thread.sleep(150);

        // Should be expired now
        assertThat(cache.get("expired-flag:user-1:production")).isEmpty();
    }

    @Test
    @DisplayName("LRU eviction — oldest-accessed entry is evicted when max size exceeded")
    void lruEviction_evictsLeastRecentlyUsed() throws InterruptedException {
        // ARRANGE — fill the cache to its maximum of 3 entries
        cache.put("flag-A:user-1:production", true, "ROLLOUT");
        Thread.sleep(5); // small delay so access times are distinct
        cache.put("flag-B:user-1:production", false, "DEFAULT");
        Thread.sleep(5);
        cache.put("flag-C:user-1:production", true, "RULE_MATCH");

        // Access flag-A again — this makes it the MOST recently used
        // After this access, the order from least-to-most-recently-used is:
        //   flag-B (never accessed since put) → flag-C → flag-A (just accessed)
        cache.get("flag-A:user-1:production");

        // ACT — add a 4th entry, which exceeds maxSize=3
        // The least-recently-used (flag-B) should be evicted
        cache.put("flag-D:user-1:production", true, "ROLLOUT");

        // ASSERT
        assertThat(cache.get("flag-A:user-1:production")).isPresent(); // kept (recently accessed)
        assertThat(cache.get("flag-C:user-1:production")).isPresent(); // kept
        assertThat(cache.get("flag-D:user-1:production")).isPresent(); // just added
        assertThat(cache.get("flag-B:user-1:production")).isEmpty();   // evicted (LRU)
    }

    @Test
    @DisplayName("invalidate() removes the specific entry")
    void invalidate_removesEntry() {
        cache.put("dark-mode:user-1:production", true, "ROLLOUT");
        assertThat(cache.get("dark-mode:user-1:production")).isPresent();

        cache.invalidate("dark-mode:user-1:production");

        assertThat(cache.get("dark-mode:user-1:production")).isEmpty();
    }

    @Test
    @DisplayName("clear() removes all entries")
    void clear_removesAllEntries() {
        cache.put("flag-A:user-1:production", true, "ROLLOUT");
        cache.put("flag-B:user-1:production", false, "DEFAULT");
        assertThat(cache.size()).isEqualTo(2);

        cache.clear();

        assertThat(cache.size()).isEqualTo(0);
        assertThat(cache.get("flag-A:user-1:production")).isEmpty();
    }

    @Test
    @DisplayName("size() returns correct count before and after operations")
    void size_tracksEntryCount() {
        assertThat(cache.size()).isEqualTo(0);

        cache.put("flag-A:user-1:production", true, "ROLLOUT");
        assertThat(cache.size()).isEqualTo(1);

        cache.put("flag-B:user-1:production", false, "DEFAULT");
        assertThat(cache.size()).isEqualTo(2);

        cache.invalidate("flag-A:user-1:production");
        assertThat(cache.size()).isEqualTo(1);
    }
}