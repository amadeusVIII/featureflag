# ============================================================
# redis.tf — ElastiCache Redis Cluster
# ============================================================
#
# CONCEPT: WHY ELASTICACHE AND NOT JUST REDIS IN A CONTAINER?
#
# In docker-compose.yml we run Redis as a container we manage.
# In production on AWS, we use ElastiCache — AWS's managed Redis.
#
# The difference:
#   Self-managed (our Docker container):
#     ✅ Full control
#     ❌ You handle: upgrades, backups, monitoring, replication, failover
#     ❌ If the container crashes and loses data: your problem
#
#   ElastiCache (managed):
#     ✅ AWS handles: automated backups, patching, monitoring, failover
#     ✅ Multi-AZ with automatic failover (if one node fails, another takes over)
#     ✅ CloudWatch metrics built in
#     ❌ Slightly more expensive (but cache.t3.micro is free tier eligible)
#
# For a portfolio project, writing this Terraform shows you understand
# how to move from "works on my laptop" to "runs in production".
# ============================================================

# ============================================================
# Subnet Group — Which Subnets ElastiCache Can Use
# ============================================================
# ElastiCache needs to know which VPC subnets to place nodes in.
# We put Redis in our PRIVATE subnets so it's never exposed to the internet.

resource "aws_elasticache_subnet_group" "redis" {
  name       = "featureflag-redis-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]

  tags = { Name = "featureflag-redis-subnet-group" }
}

# ============================================================
# Parameter Group — Redis Configuration
# ============================================================
# A parameter group is like a Redis configuration file.
# We start with the default Redis 7.x settings, which is fine for our use case.
# If we needed to tune maxmemory-policy or other settings, we'd do it here.

resource "aws_elasticache_parameter_group" "redis" {
  name   = "featureflag-redis-params"
  family = "redis7"  # Must match the engine version below

  # We use the allkeys-lru eviction policy.
  # WHY: When Redis runs out of memory, it needs to decide what to evict.
  # "allkeys-lru" evicts the least-recently-used key.
  # For a flag cache, this is exactly right: rarely-accessed flags are evicted first.
  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
}

# ============================================================
# ElastiCache Cluster — The Managed Redis Instance
# ============================================================
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "featureflag-redis"
  engine               = "redis"
  engine_version       = "7.0"                         # Match our docker-compose version
  node_type            = var.redis_node_type            # cache.t3.micro (free tier)
  num_cache_nodes      = 1                             # Single node (not clustered mode)
  parameter_group_name = aws_elasticache_parameter_group.redis.name
  port                 = 6379

  # Place Redis in our private subnets (not accessible from the internet)
  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  # Automatic backups — snapshot at 3am UTC daily, keep for 7 days.
  # For a cache this isn't critical (we can rebuild from PostgreSQL),
  # but it's good practice to document that you considered it.
  snapshot_retention_limit = 7
  snapshot_window          = "03:00-04:00"

  # Apply changes immediately rather than waiting for a maintenance window.
  # In production you might set this to false to control when disruption happens.
  apply_immediately = true

  tags = { Name = "featureflag-redis" }
}
