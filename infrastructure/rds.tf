# ============================================================
# rds.tf — RDS PostgreSQL Instance
# ============================================================
#
# CONCEPT: WHY RDS AND NOT JUST POSTGRES IN A CONTAINER?
#
# Same reasoning as ElastiCache vs. Redis container.
# RDS (Relational Database Service) is AWS's managed PostgreSQL.
#
# Key benefits over a self-managed PostgreSQL container:
#   ✅ Automated daily backups with point-in-time recovery
#      (restore to any second in the last 7 days)
#   ✅ Automated minor version upgrades
#   ✅ Multi-AZ: AWS keeps a standby replica in a second AZ.
#      If the primary fails, it automatically promotes the standby.
#      Failover takes ~60 seconds. Zero manual intervention.
#   ✅ Storage auto-scaling (never run out of disk)
#   ✅ CloudWatch metrics out of the box
#
# For this portfolio project:
#   - Multi-AZ is disabled (doubles cost, not needed for demo)
#   - db.t3.micro is AWS Free Tier eligible (750 hours/month)
#   - We DO set up backups to show production-readiness thinking
# ============================================================

# ============================================================
# DB Subnet Group — Which Subnets RDS Can Use
# ============================================================
# RDS requires a subnet group. We put the database in PRIVATE
# subnets so it is never accessible from the internet.
# Only our ECS tasks (via security group rules) can connect.

resource "aws_db_subnet_group" "postgres" {
  name       = "featureflag-db-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]

  tags = { Name = "featureflag-db-subnet-group" }
}

# ============================================================
# RDS PostgreSQL Instance
# ============================================================
resource "aws_db_instance" "postgres" {
  identifier = "featureflag-postgres"   # Name shown in the AWS console

  # Engine configuration
  engine         = "postgres"
  engine_version = "15"                 # Match our docker-compose version
  instance_class = var.rds_instance_class  # db.t3.micro (free tier)

  # Storage
  allocated_storage     = 20            # 20 GB initial storage
  max_allocated_storage = 100           # Auto-scale up to 100 GB if needed
  storage_type          = "gp2"         # General Purpose SSD (good balance of cost/performance)
  storage_encrypted     = true          # Encrypt data at rest (security best practice)

  # Database credentials
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password           # Sensitive variable — never hardcoded

  # Network — place in private subnets, block public access
  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false        # CRITICAL: never expose the DB to the internet

  # Backup configuration
  backup_retention_period = 7           # Keep backups for 7 days (point-in-time recovery)
  backup_window           = "02:00-03:00" # Take backups at 2am UTC (low traffic)
  maintenance_window      = "Mon:04:00-Mon:05:00" # Apply patches Monday 4am UTC

  # For portfolio purposes, Multi-AZ is disabled (would double cost).
  # In production: set multi_az = true for automatic failover.
  multi_az = false

  # IMPORTANT: skip_final_snapshot = true means if you run "terraform destroy",
  # the database is deleted WITHOUT taking a final backup.
  # For a demo/portfolio project this is fine.
  # In production: set skip_final_snapshot = false and set a final_snapshot_identifier.
  skip_final_snapshot = true

  # Performance Insights: Gives you a visual query performance dashboard.
  # Useful for diagnosing slow queries. Free for the first 7 days of retention.
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  # CloudWatch monitoring
  monitoring_interval = 60  # Enhanced monitoring every 60 seconds
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  tags = { Name = "featureflag-postgres" }
}

# ============================================================
# IAM Role for RDS Enhanced Monitoring
# ============================================================
# RDS needs permission to send monitoring metrics to CloudWatch.

resource "aws_iam_role" "rds_monitoring" {
  name = "featureflag-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring_policy" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
