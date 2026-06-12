# ADR 002: Redis Pub/Sub vs. Polling for Distributed Cache Invalidation

**Status:** Accepted
**Date:** 2025-05-03
**Authors:** FeatureFlag Team
**Deciders:** Full engineering team

---

## Context

When a flag is updated via the admin API, every running instance of the Flag Service
must invalidate its cached copy of that flag. If it does not, instances will continue
serving stale flag values until the 5-minute TTL expires.

This is a distributed systems problem. In local development with a single API container,
it is trivial — there is only one cache to invalidate. In production with two or more
ECS tasks running behind a load balancer, updating one instance's cache does nothing for
the others.

We evaluated two approaches to solving this problem.

---

## Problem Illustration

Consider two API instances (A and B) running in production:

```
Time 0:  Both instances have cached: dark-mode = ENABLED (cached 3 minutes ago)

Time 1:  Admin disables dark-mode via PUT /admin/flags/dark-mode
         AdminService writes to PostgreSQL: dark-mode = DISABLED

Time 2:  Without invalidation:
           Instance A still serves: dark-mode = ENABLED  ← WRONG
           Instance B still serves: dark-mode = ENABLED  ← WRONG
         Both instances serve stale data for up to 2 more minutes (until TTL expires)
```

This means users could see inconsistent behavior — some API requests would return the
new value (if they happen to hit after a cache miss) and some would return the old value.
For a feature flag that disables a broken feature, this inconsistency is a real problem.

---

## Options Considered

### Option A: TTL-Only (rejected)

Do nothing special for invalidation. Rely on the 5-minute TTL to eventually correct
stale cache entries.

**What is appealing about this:**
- Zero implementation complexity. Already implemented by the TTL in `FlagCacheService`.
- No additional infrastructure needed.

**Why we rejected it:**

The product promise of a feature flag system is: "toggle a flag and the change takes
effect immediately." A 5-minute propagation delay breaks this promise in a noticeable way.
If you toggle off a feature during an incident because it is causing errors, you need that
change to propagate in seconds — not minutes.

TTL is a safety net for when invalidation fails, not a primary strategy.

---

### Option B: Polling (rejected)

Each instance runs a background thread that polls PostgreSQL (or Redis) every N seconds
for flag changes. If a change is detected, the instance invalidates its own cache.

**What is appealing about this:**
- Simple to implement with `@Scheduled` in Spring.
- No new infrastructure dependencies.

**Why we rejected it:**

1. **Latency is bounded by N.** If N = 10 seconds, changes can take up to 10 seconds to
   propagate. If N = 1 second, we add 1 query per instance per second to PostgreSQL.
   With 3 instances and 1-second polling: 3 extra DB queries/second, just to check for
   changes that rarely happen. This is wasted load.

2. **Thundering herd risk.** If the polling interval is synchronized (e.g., all instances
   start their timer at deploy time), they all hit the database at exactly the same moment.
   Under load, this "thundering herd" can spike PostgreSQL latency.

3. **It is a "pull" model in a problem that calls for "push".** The flag change already
   happened — the database already knows about it. There is no reason to poll; we just need
   to broadcast the event to all listeners.

---

### Option C: Redis Pub/Sub (chosen)

When a flag is updated, the Admin Service publishes a message to a Redis channel. All
running Flag Service instances are subscribed to that channel and receive the message
simultaneously. Each instance immediately deletes its cached entry for that flag.

```
Time 1:  Admin updates dark-mode via PUT /admin/flags/dark-mode
         AdminService writes to PostgreSQL: dark-mode = DISABLED
         AdminService publishes to Redis channel 'flag-updates': "dark-mode:production"

Time 2:  (~100ms later)
           Instance A receives message, deletes Redis key: flag:dark-mode:production
           Instance B receives message, deletes Redis key: flag:dark-mode:production

Time 3:  Next evaluation request to either instance:
           Cache MISS → fresh DB query → re-cache with new value (DISABLED)
```

**What is appealing about this:**

- Event-driven: the message is sent exactly when a change happens. No polling waste.
- Latency: Redis Pub/Sub message delivery takes ~1–50ms on a local network. All instances
  are invalidated within ~100ms of the flag change.
- No new infrastructure: Redis is already in our stack for the L2 cache. Pub/Sub is a
  built-in Redis feature — zero additional cost or complexity.
- Spring Data Redis has built-in support via `RedisMessageListenerContainer`.

**What is concerning about this:**

- **Redis is now a single point of failure for invalidation.** If Redis goes down, flag
  updates will not propagate. Instances will serve stale cache entries until the TTL
  expires. This is mitigated by the 5-minute TTL (Option A becomes our fallback).

- **At-most-once delivery.** Redis Pub/Sub does not guarantee delivery. If an instance
  starts up after a message was published, it will miss that message. This is acceptable
  because: (a) new instances start with an empty cache, so they will always query
  PostgreSQL on first access, and (b) the TTL ensures eventual consistency.

---

## Decision

We chose **Option C: Redis Pub/Sub**, with the 5-minute TTL (Option A) as a fallback
safety net.

The propagation guarantee is: changes reach all running instances within ~100ms. In the
rare case of a Redis outage, stale data self-corrects within 5 minutes.

This tradeoff is explicitly documented in the README and is a strong interview talking
point: we chose an event-driven approach to meet the product latency requirement, and
we designed a fallback for the known failure mode.

---

## Consequences

**Positive:**

- Flag changes propagate to all instances in under 100ms — sufficient for incident response.
- No database polling overhead.
- Redis infrastructure is already present — zero additional cost.
- The live demo (docker compose up --scale api=3, update flag, watch all three cache keys
  disappear in Redis Commander) is made possible by this choice.

**Negative:**

- Redis is now a required dependency for correct invalidation behavior (though not for
  basic functionality — the service degrades gracefully without it).
- Additional complexity: `FlagChangePublisher`, `FlagChangeListener`, `RedisConfig` wiring.
  This complexity is justified by the product requirement.

---

## Failure Mode Documentation

**Redis is down:**
- Cache-aside still works: misses fall through to PostgreSQL directly.
- Pub/Sub does not work: flag updates do not propagate.
- Outcome: stale cache entries persist until their 5-minute TTL expires.
- Recovery: automatic when Redis comes back. No operator intervention required.

**Redis message is not delivered (lost in transit):**
- This is indistinguishable from the "Redis is down" scenario.
- Same mitigation: TTL-based eventual consistency.

**Instance restarts after a flag update:**
- New instance starts with empty Redis cache.
- All first evaluations are cache misses → PostgreSQL → correct current value.
- No stale data issue for new instances.

---

## References

- [Redis Pub/Sub documentation](https://redis.io/docs/manual/pubsub/)
- [Spring Data Redis: Message Listener Container](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:messaging)
- `api/src/main/java/com/featureflag/cache/FlagChangePublisher.java`
- `api/src/main/java/com/featureflag/cache/FlagChangeListener.java`
- `api/src/main/java/com/featureflag/cache/RedisConfig.java`
