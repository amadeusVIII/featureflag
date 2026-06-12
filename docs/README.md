# FeatureFlag Service

> Self-hosted feature flag management with sub-millisecond evaluation,
> Redis-backed distributed caching, and a publishable Java SDK.

[![CI](https://github.com/amadeusVIII/featureflag/actions/workflows/ci.yml/badge.svg)](https://github.com/amadeusVIII/featureflag/actions/workflows/ci.yml)
[![Security Scan](https://github.com/amadeusVIII/featureflag/actions/workflows/security.yml/badge.svg)](https://github.com/amadeusVIII/featureflag/actions/workflows/security.yml)
[![SDK on GitHub Packages](https://img.shields.io/badge/SDK-GitHub%20Packages-blue)](https://github.com/amadeusVIII/featureflag/packages)

---

## What This Is



FeatureFlag is a self-hosted alternative to LaunchDarkly or Unleash,
built from scratch to solve a real distributed systems problem:
how do you decouple deployments from releases while keeping
feature evaluation fast and consistent across multiple instances?

The system uses a three-layer evaluation architecture:
an in-process SDK cache, Redis as a shared cache layer,
and Redis Pub/Sub for event-driven invalidation. This delivers
sub-millisecond evaluations on warm paths and propagates flag
updates across instances in under 100ms—without polling,
manual cache clearing, or redeployments.

Beyond the runtime system, the project includes a publishable Java SDK,
a React admin dashboard, Testcontainers integration tests,
CI/CD pipelines with security scanning, and Terraform-managed
infrastructure to showcase production-grade backend engineering.
---

## Architecture

```
  React Admin          Java SDK
  Dashboard            (Published)
      |                     |
      |   HTTP REST         |   HTTP REST
      v                     v
  +-----------------------------------------------+
  |         Nginx (Port 80 — entry point)          |
  +--+------------------+-------------------------+
     |                  |
     v                  v
  +--------+       +-----------+
  |  Flag  |       |   Admin   |
  | Service|       |  Service  |
  | :8081  |       |  :8082    |
  +---+----+       +-----+-----+
      |                  |
      +--------+---------+
               |
        +------v-------+
        |    Redis      |
        | Cache + Pub/Sub|
        +------+-------+
               |
        +------v-------+
        |  PostgreSQL   |
        | Source of truth|
        +--------------+
```

The system is a **modular monolith** — a single deployable Spring Boot JAR with clearly
separated internal modules (flag domain, user domain, cache layer, security layer). This is
the same architecture used by Shopify and Stack Overflow: it demonstrates the same design
thinking as microservices without the operational overhead of service discovery and distributed
tracing.

---

## The Caching Architecture — The Technical Core

This project implements **three distinct layers of caching**. Each layer exists for a
specific reason. Understanding all three and how they interact is the main thing this
project demonstrates.

| Layer | Location | Mechanism | Latency | TTL | Purpose |
|---|---|---|---|---|---|
| **L1** | SDK in-process | LinkedHashMap LRU | ~0.1ms | 30s | Zero network on hot paths |
| **L2** | Redis (server-side) | Cache-aside pattern | ~1ms | 5min | Avoid DB queries under load |
| **L3** | Redis Pub/Sub | Event-driven invalidation | <100ms | Event-driven | Keep all instances consistent |

### How a flag evaluation flows through the layers

```
Client app calls: client.isEnabled("dark-mode", userId)

L1 HIT  → returns in 0.1ms from process memory. Zero network.
L1 MISS → HTTP call to Flag Service

  Flag Service:
  L2 HIT  → Redis returns JSON in ~1ms. Response header: X-Cache-Status: HIT
  L2 MISS → PostgreSQL query (~45ms). Write to Redis. Response header: X-Cache-Status: MISS
  
  SDK stores result in L1 for 30 seconds.
```

### How a flag update propagates to all instances

```
Admin updates flag → PostgreSQL write
                   → Redis Pub/Sub message: "dark-mode:production"
                       ↓               ↓               ↓
                   Instance A     Instance B     Instance C
                   deletes L2    deletes L2    deletes L2
                   cache key     cache key     cache key
                                (~100ms total)
```

Without Pub/Sub, instances behind a load balancer would serve stale cached data for up to
5 minutes after a flag change. With Pub/Sub, propagation takes under 100ms regardless of
how many instances are running.

---

## Quick Start

```bash
# Clone the repository
git clone https://github.com/amadeusVIII/featureflag.git
cd featureflag

# Copy environment variables template
cp .env.example .env
# Edit .env with your values (DB password, JWT secret)

# Start all 5 services
docker compose up -d

# Open the admin dashboard
open http://localhost

# Open Redis Commander (dev tool — inspect the cache live)
open http://localhost:8001
```

**What starts:**

| Service | URL | What it is |
|---|---|---|
| Nginx | http://localhost | Entry point — routes to API and frontend |
| React Admin | http://localhost | Flag management dashboard |
| Spring Boot API | http://localhost:8080 | REST API (via Nginx) |
| Redis Commander | http://localhost:8001 | GUI to inspect Redis cache keys live |
| PostgreSQL | localhost:5432 | Database (accessible via DBeaver/DataGrip in dev) |

---

## Live Demo: Cache Invalidation Across Multiple Instances

This is the most important thing to demonstrate. Run this to see Pub/Sub invalidation live:

```bash
# Start 3 API instances behind the same Nginx load balancer
docker compose up -d --scale api=3

# Open Redis Commander in your browser
open http://localhost:8001
```

1. Create a flag called `dark-mode` in the admin dashboard
2. Evaluate it once — watch a Redis key appear: `flag:dark-mode:production`
3. Update the flag in the admin dashboard
4. Watch **all three instances' cache keys disappear simultaneously** in Redis Commander
5. The next evaluation triggers a fresh DB read and re-populates the cache

This propagation happens in under 100ms. No polling. No manual cache clearing. No redeploy.

---

## Performance Results

Benchmark: `k6 run tests/load/flag-evaluation.js`
Configuration: 50 concurrent virtual users, 30-second run

| Metric | Result | Notes |
|---|---|---|
| Latency p95 (Overall) | **13.63ms** | Reflects 99.6% cache hit rate |
| Max Latency (Cache MISS) | **399.51ms** | Initial PostgreSQL query + Redis write |
| Requests/second | **~460** | 13,870 requests completed over 30 seconds |
| Error rate | **0%** | Zero failed HTTP requests |
| Cache hit rate | **~99.6%** | 13,820 hits / 50 misses (warm-up phase) |

> Run `make load-test` to reproduce these results locally.
---

## SDK Usage

The Java client SDK is published to GitHub Packages and requires 3 lines to integrate:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.featureflag</groupId>
    <artifactId>featureflag-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
// Initialize once (e.g., in a Spring @Bean)
FeatureFlagClient client = FeatureFlagClient.builder()
    .serverUrl("https://your-featureflag-server.com")
    .apiKey("your-api-key")
    .localCacheTtl(Duration.ofSeconds(30))  // L1 cache TTL
    .build();

// Evaluate anywhere — costs ~0.1ms on hot paths (L1 cache hit)
boolean showNewCheckout = client.isEnabled("new-checkout-flow", userId);

// Pre-warm cache on startup (optional — fetches all flags at once)
client.warmCache("production");
```

[→ Full SDK documentation](sdk/README.md)
[→ SDK on GitHub Packages](https://github.com/YOUR_USERNAME/featureflag/packages)

---

## API Reference

### Flag Evaluation (SDK-facing, high-traffic)

```
GET  /api/v1/flags/evaluate?key=dark-mode&userId=user-123&environment=production
POST /api/v1/flags/evaluate-batch   (evaluate multiple flags in one call)
GET  /api/v1/flags/snapshot         (download all flags — for cache warm-up)
```

**Response example:**

```json
{
  "flagKey": "dark-mode",
  "enabled": true,
  "reason": "RULE_MATCH",
  "cachedAt": "2025-05-10T14:22:11Z",
  "servedFromCache": true,
  "evaluationTimeMs": 1
}
```

The `X-Cache-Status: HIT|MISS` response header is set on every evaluation response.

### Admin API (JWT-authenticated)

```
GET    /api/v1/admin/flags              List all flags
POST   /api/v1/admin/flags              Create flag
PUT    /api/v1/admin/flags/{id}         Update flag (triggers cache invalidation)
PATCH  /api/v1/admin/flags/{id}/toggle  Toggle enabled/disabled
DELETE /api/v1/admin/flags/{id}         Delete flag
GET    /api/v1/admin/flags/{id}/audit   Full audit history
```

[→ Full API reference](docs/api-reference.md)
[→ OpenAPI/Swagger UI at http://localhost:8080/swagger-ui.html (dev only)](http://localhost:8080/swagger-ui.html)

---

## CI/CD Pipeline

Three GitHub Actions workflows run automatically:

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Every push + PR | Checkstyle lint → unit tests → Testcontainers integration tests → Docker build + smoke test |
| `cd.yml` | Merge to `main` | Publish SDK to GitHub Packages → push Docker image to GHCR → deploy to Railway |
| `security.yml` | Weekly + `main` push | Trivy container scan for CRITICAL/HIGH CVEs → upload to GitHub Security tab |

**The integration tests (Testcontainers) spin up real Redis and PostgreSQL containers** for each
test run. They assert:
- That a second evaluation of the same flag returns from cache (`servedFromCache: true`)
- That evaluating after a flag update triggers a cache miss (`servedFromCache: false`)
- That cache invalidation propagation takes under 200ms

No mocking of infrastructure. The tests prove the caching behavior actually works.

[→ View latest CI run](https://github.com/YOUR_USERNAME/featureflag/actions)

---

## Project Structure

```
featureflag/
├── api/                          # Spring Boot application
│   └── src/main/java/com/featureflag/
│       ├── api/                  # REST controllers + DTOs
│       ├── domain/               # Flag, User, Audit business logic
│       ├── cache/                # Redis cache layer (the interesting part)
│       │   ├── FlagCacheService.java     # Cache-aside get/put/invalidate
│       │   ├── FlagChangePublisher.java  # Pub/Sub: publish invalidation event
│       │   ├── FlagChangeListener.java   # Pub/Sub: receive + act on event
│       │   └── RedisConfig.java          # Wire the listener to the channel
│       └── security/             # JWT filter chain
│
├── sdk/                          # Publishable Java client SDK
│   └── src/main/java/com/featureflag/sdk/
│       ├── FeatureFlagClient.java        # Public API — 3 methods
│       └── internal/
│           ├── LocalFlagCache.java       # L1: LRU in-process cache
│           └── FlagApiClient.java        # HTTP client to Flag Service
│
├── frontend/                     # React admin dashboard
├── infrastructure/               # Terraform AWS definitions
│   ├── networking.tf             # VPC, subnets, ALB, security groups
│   ├── ecs.tf                    # ECS Fargate cluster + task definitions
│   ├── redis.tf                  # ElastiCache Redis
│   └── rds.tf                    # RDS PostgreSQL
│
├── docs/
│   └── adr/                      # Architecture Decision Records
│       ├── 001-why-redis-cache-aside.md
│       ├── 002-pubsub-vs-polling.md
│       └── 003-sdk-local-cache-strategy.md
│
├── .github/workflows/
│   ├── ci.yml
│   ├── cd.yml
│   └── security.yml
│
├── docker-compose.yml            # 5-service local environment
├── docker-compose.override.yml   # Dev extras (Redis Commander, debug port)
└── Makefile                      # Shortcut commands
```

---

## Makefile Commands

```bash
make start           # docker compose up -d (all 5 services)
make start-scaled    # docker compose up -d --scale api=3 (demo cache invalidation)
make test            # Run unit tests
make test-integration # Run Testcontainers integration tests
make load-test       # Run k6 load test (requires k6 installed)
make logs            # Tail all service logs
make redis-cli       # Open Redis CLI inside the container
make clean           # Stop and remove all containers and volumes
```

---

## Architecture Decision Records

This project includes three ADRs documenting the key technical decisions:

- [ADR 001: Why Redis cache-aside over Spring @Cacheable](docs/adr/001-why-redis-cache-aside.md)
  — Why we wrote cache logic manually instead of using the `@Cacheable` annotation

- [ADR 002: Redis Pub/Sub vs. polling for cache invalidation](docs/adr/002-pubsub-vs-polling.md)
  — Why flag changes propagate in 100ms instead of N seconds

- [ADR 003: SDK local in-process cache design](docs/adr/003-sdk-local-cache-strategy.md)
  — Why the SDK uses `LinkedHashMap` with LRU over Caffeine or Guava

ADRs capture *why* decisions were made, not just what was built. They are the primary way a
reader can understand the reasoning behind the architecture without asking the team.

---

## Cloud Deployment (AWS)

The `/infrastructure` folder contains Terraform definitions for deploying to AWS.
This mirrors the Docker Compose setup exactly:

| Docker Compose service | AWS equivalent |
|---|---|
| `api` container | ECS Fargate task (2 replicas minimum) |
| `redis` container | ElastiCache Redis (cache.t3.micro — free tier) |
| `postgres` container | RDS PostgreSQL (db.t3.micro — free tier) |
| Nginx | Application Load Balancer |

```bash
# Initialize Terraform (first time only)
cd infrastructure
terraform init

# Preview what will be created
terraform plan -var="db_password=yourpassword" -var="jwt_secret=yourjwtsecret"

# Create all AWS resources
terraform apply
```

The live demo runs on Railway (zero-cost):
[→ https://featureflag.up.railway.app](https://featureflag.up.railway.app)

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| API | Spring Boot | 3.2.x (Java 21) |
| ORM | Spring Data JPA + Flyway | — |
| Cache | Spring Data Redis | Redis 7.x |
| Auth | Spring Security + jjwt | 0.12.x |
| Testing | JUnit 5 + Testcontainers | — |
| Frontend | React 18 + Vite + TailwindCSS | — |
| Database | PostgreSQL | 15 |
| Containers | Docker + Docker Compose v2 | — |
| CI/CD | GitHub Actions | — |
| Security scan | Trivy | — |
| Load testing | k6 | — |
| IaC | Terraform | 1.6+ |
| SDK publishing | GitHub Packages (Maven) | — |

---

## Running Tests

```bash
# Unit tests (no infrastructure required, ~10 seconds)
./mvnw test -pl api,sdk

# Integration tests (spins up real Redis + PostgreSQL via Testcontainers, ~90 seconds)
./mvnw verify -pl api -Pintegration-tests

# Load test (requires k6: https://k6.io/docs/getting-started/installation)
docker compose up -d
k6 run tests/load/flag-evaluation.js
```
