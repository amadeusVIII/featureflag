# ADR 001: Redis Cache-Aside Pattern vs. Spring @Cacheable

**Status:** Accepted
**Date:** 2025-05-01
**Authors:** FeatureFlag Team
**Deciders:** Full engineering team

---

## Context

The flag evaluation endpoint (`GET /api/v1/flags/evaluate`) is the highest-traffic path
in the entire system. Every SDK client calls it on every flag evaluation. At scale, this
endpoint could receive thousands of requests per second.

Without caching, every evaluation requires a PostgreSQL query. PostgreSQL typically takes
30–80ms for a simple lookup. At 1,000 requests/second, that is 1,000 concurrent DB
connections — well beyond what a single `db.t3.micro` instance can handle.

We needed a caching solution. We evaluated two approaches:

---

## Options Considered

### Option A: Spring @Cacheable (rejected)

Spring Boot provides `@Cacheable`, an annotation that automatically caches method return
values. Usage looks like this:

```java
@Cacheable(value = "flags", key = "#flagKey + ':' + #environment")
public FlagConfig getFlag(String flagKey, String environment) {
    return flagRepository.findByKeyAndEnvironment(flagKey, environment);
}
```

**What is appealing about this:**
- Extremely concise — one annotation does everything
- Spring handles cache population and eviction automatically
- Works with any cache backend (Caffeine, Redis, EhCache)

**Why we rejected it:**

1. **Invisible behavior is the enemy of learning.** The annotation hides everything that
   makes caching interesting: when does a cache miss happen? What does the fallback path
   look like? How is the TTL applied? On a portfolio project where the caching layer is
   the main demonstration of skill, hiding it behind an annotation defeats the purpose.

2. **We cannot add the `X-Cache-Status` response header.** When an evaluation is served
   from cache, we want to set `X-Cache-Status: HIT` in the response. This is visible in
   the live demo and in the k6 load test metrics. `@Cacheable` gives us no hook to detect
   whether the result came from cache or from the database.

3. **No custom fallback behavior.** When Redis is unavailable, `@Cacheable` throws a
   `RedisConnectionException`. We want to catch that exception and fall through to
   PostgreSQL instead. Wrapping `@Cacheable` in a try-catch is awkward and non-obvious.

4. **No per-request TTL control.** We want different TTLs for different environments
   (production flags cached longer than staging flags). `@Cacheable` TTLs are configured
   globally in `application.yml`, not per-call.

### Option B: Manual Cache-Aside in FlagCacheService (chosen)

We implement the cache logic explicitly in `FlagCacheService.java`. The calling code in
`FlagService` follows the three-step cache-aside pattern manually:

```java
// Step 1: Check cache
Optional<FlagConfig> cached = cacheService.get(flagKey, environment);
if (cached.isPresent()) {
    response.setHeader("X-Cache-Status", "HIT");
    return cached.get();
}

// Step 2: Miss — query the database
FlagConfig config = flagRepository.findByKeyAndEnvironment(flagKey, environment)
    .orElseThrow(() -> new FlagNotFoundException(flagKey));

// Step 3: Populate cache before returning
cacheService.put(flagKey, environment, config);
response.setHeader("X-Cache-Status", "MISS");
return config;
```

---

## Decision

We chose **Option B: Manual Cache-Aside**.

---

## Consequences

**Positive:**

- The caching logic is fully visible and understandable by any developer reading the code.
  No magic. No annotations to look up.

- We can set `X-Cache-Status: HIT|MISS` in every response, enabling our k6 load tests
  to assert different latency thresholds for cache hits vs. misses.

- We can wrap Redis calls in try-catch and fall through to PostgreSQL if Redis is down,
  making the system resilient to cache outages.

- We can control TTL programmatically:
  ```java
  Duration ttl = "production".equals(environment)
      ? Duration.ofMinutes(5)
      : Duration.ofMinutes(1); // Shorter TTL in staging for faster iteration
  ```

- The evaluation timing (`evaluationTimeMs` in the response) accurately reflects whether
  the result came from Redis (`<2ms`) or PostgreSQL (`>20ms`), giving us real performance
  data to document in the README.

**Negative:**

- More boilerplate code than `@Cacheable`.

- We are responsible for ensuring the cache-aside pattern is applied consistently wherever
  flags are read. A new developer could accidentally query the DB directly and bypass the
  cache. We mitigate this by making `FlagCacheService` the only way to interact with Redis,
  and documenting the rule in the codebase.

---

## Alternatives We Did Not Evaluate

**Write-through caching:** On every PostgreSQL write, also write to Redis immediately.
We rejected this because our write path (admin flag updates) is rare compared to the
read path (SDK evaluations). Write-through adds complexity to the admin path without
meaningful benefit. Cache-aside with a short TTL is simpler and sufficient.

---

## References

- [Redis Cache-Aside Pattern documentation](https://redis.io/docs/manual/patterns/cache-aside/)
- `api/src/main/java/com/featureflag/cache/FlagCacheService.java`
- `api/src/main/java/com/featureflag/domain/flag/FlagService.java`
