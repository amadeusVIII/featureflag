# ADR 003: SDK Local In-Process Cache Design

**Status:** Accepted
**Date:** 2025-05-07
**Authors:** FeatureFlag Team
**Deciders:** Full engineering team

---

## Context

The FeatureFlag Java SDK is used inside client applications — mobile apps, web backends,
microservices. The most common usage pattern is:

```java
// Called on every incoming HTTP request to the client application
boolean showNewCheckout = client.isEnabled("new-checkout", userId);
```

If `isEnabled()` makes a network call to our Flag Service every time it is invoked, and
a busy web server handles 500 requests/second, that is 500 HTTP calls/second to our
Flag Service — from a single client application. With 10 client applications, that is
5,000 HTTP calls/second to a service that is doing nothing but answering "is this flag
on?".

This is clearly wrong. We needed to decide how the SDK should handle caching locally,
inside the client application process.

---

## Options Considered

### Option A: No client-side cache — always call the server (rejected)

Every `isEnabled()` call makes an HTTP request to the Flag Service.

**What is appealing about this:**
- Flag evaluations always reflect the absolute latest value.
- Zero complexity in the SDK.

**Why we rejected it:**

This creates a 1:1 coupling between the client application's request throughput and our
Flag Service's load. It also adds 1–5ms of network latency to every application request
that checks a flag. For a "dark mode" toggle that barely changes, this is a terrible
tradeoff. Network calls are orders of magnitude slower than a HashMap lookup.

---

### Option B: Time-based expiry cache (TTL cache) — chosen

The SDK caches flag evaluation results in memory. Each entry has a TTL (Time To Live).
After the TTL expires, the next `isEnabled()` call refreshes the entry from the server.

Concretely: `isEnabled("dark-mode", userId)` is called 1,000 times in 30 seconds.
Only the FIRST call makes a network request. Calls 2–1,000 return the cached value
in-process with zero network overhead.

**What is appealing about this:**
- Dramatic reduction in network calls. One call per (flag, user) pair per TTL period,
  regardless of how often `isEnabled()` is invoked.
- Zero added latency for cached calls (a HashMap lookup takes ~100 nanoseconds).
- Acceptable staleness: flag values change infrequently. A 30-second window of potential
  staleness is typically fine for features like "show new checkout flow" or "enable dark mode".

**Concerns:**

- **Stale data risk:** If a flag is disabled during an incident (e.g., the new checkout
  flow has a bug), SDK clients will continue to serve the old value for up to 30 seconds
  before the cache expires. This is a known and accepted tradeoff. 30 seconds is fast enough
  for incident response and avoids the network-call-per-request problem.

- **Memory usage:** If a client application checks flags for 100,000 unique user IDs,
  the cache stores 100,000 entries. We address this with LRU eviction (see below).

---

### Option C: Background refresh cache (considered, not chosen for v1)

The SDK proactively refreshes cache entries in a background thread before they expire.
The `isEnabled()` call always returns the in-memory value immediately, and a background
thread silently re-fetches values that are approaching expiry.

**What is appealing about this:**
- Zero "cold" misses after warm-up: users never see the latency spike of a network call.
- The cache is always warm.

**Why deferred to v2:**

This adds complexity: a background thread, thread pool management, shutdown hooks, and
error handling for background refresh failures. For the SDK's first release, the simpler
TTL-based approach (Option B) meets the requirements. Background refresh is documented
as a v2 enhancement.

---

## Decision

We chose **Option B: TTL-based in-process cache**, implemented as a `LinkedHashMap`
with LRU eviction.

### Why LinkedHashMap with LRU eviction?

A plain `HashMap` grows unbounded. For a client that evaluates flags for thousands of
unique user IDs, this would be a memory leak. We bound the cache with LRU (Least
Recently Used) eviction:

- The cache holds at most `maxSize` entries (default: 1,000).
- When the cache is full and a new entry arrives, the least-recently-used entry is evicted.
- This is implemented using `LinkedHashMap` with `accessOrder = true`:

```java
new LinkedHashMap<>(maxSize, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;  // Evict when over capacity
    }
}
```

### Why not Caffeine or Guava Cache?

Caffeine and Guava are excellent caching libraries with TTL and LRU support. We chose
`LinkedHashMap` for three reasons:

1. **Zero dependencies.** The SDK's `pom.xml` should have as few dependencies as possible.
   Every dependency we add becomes a potential version conflict for SDK consumers. An SDK
   that imports Guava forces the consumer to use a compatible Guava version. A `LinkedHashMap`
   is part of the JDK — no dependency required.

2. **Transparency.** The implementation is approximately 30 lines of code. A developer
   reading the SDK source can understand exactly how the cache works without knowing an
   external library. This is a deliberate choice for a learning-oriented codebase.

3. **Sufficient for our use case.** We do not need the advanced features of Caffeine
   (async loading, statistics, size-based eviction by weight). A TTL check on get() and
   LRU eviction via LinkedHashMap is everything we need.

**Acknowledged limitation:** `LinkedHashMap` requires external synchronization. We wrap
it in `Collections.synchronizedMap()`. For high-concurrency environments, `ConcurrentHashMap`
with manual TTL tracking would be more scalable. This is documented as a v2 improvement.

---

## Cache Configuration Defaults

| Parameter | Default | Rationale |
|---|---|---|
| TTL | 30 seconds | Short enough to propagate most flag changes quickly; long enough to absorb high-throughput call patterns |
| Max size | 1,000 entries | Sufficient for most applications; bounded to prevent unbounded memory growth |

Both are configurable via the `FeatureFlagClient.Builder`:

```java
FeatureFlagClient client = FeatureFlagClient.builder()
    .serverUrl("https://flags.yourcompany.com")
    .apiKey("your-api-key")
    .localCacheTtl(Duration.ofSeconds(10))  // Shorter for staging/debug
    .localCacheMaxSize(500)                  // Smaller for memory-constrained environments
    .build();
```

---

## Interaction With Server-Side Cache

The SDK's local cache (L1) stacks on top of the server-side Redis cache (L2):

```
isEnabled() call #1 (SDK L1 miss):
  → HTTP call to Flag Service
  → Flag Service: L2 Redis HIT → returns in ~2ms
  → SDK stores result in L1 for 30 seconds

isEnabled() call #2–N within 30 seconds (SDK L1 HIT):
  → Returns from L1 in-process memory
  → Zero network activity
  → Latency: ~100 nanoseconds
```

The worst case (both L1 and L2 miss) is still bounded:
- SDK L1 miss → HTTP request to Flag Service
- Flag Service L2 miss → PostgreSQL query (~45ms)
- Total: ~50ms round trip
- This happens at most once per flag/user combination per 30 seconds

---

## Consequences

**Positive:**

- `isEnabled()` is effectively free on hot paths after the first call per TTL window.
- Network load on the Flag Service is independent of the client application's request rate.
- Memory usage is bounded by `maxSize`.
- Zero external dependencies in the SDK.

**Negative:**

- Flag changes can take up to 30 seconds to reach SDK clients.
- LRU eviction with `synchronizedMap` has lock contention under high concurrency.
  At very high call rates (>10k/second on a single SDK instance), this could become
  a bottleneck. Mitigated by upgrading to a Caffeine-based cache in v2 if needed.

---

## References

- [Java LinkedHashMap removeEldestEntry](https://docs.oracle.com/en/java/docs/api/java.base/java/util/LinkedHashMap.html#removeEldestEntry(java.util.Map.Entry))
- [Caffeine Cache library](https://github.com/ben-manes/caffeine) — v2 upgrade candidate
- `sdk/src/main/java/com/featureflag/sdk/internal/LocalFlagCache.java`
- `sdk/src/main/java/com/featureflag/sdk/FeatureFlagClient.java`
