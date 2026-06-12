# ============================================================
# variables.tf — Input variables
# ============================================================
#
# Variables are the "parameters" of your infrastructure.
# You define them here (type, description, default).
# You supply their values via:
#   1. A terraform.tfvars file (never commit this — it has secrets)
#   2. Environment variables: TF_VAR_db_password=...
#   3. -var flags: terraform apply -var="db_password=..."
#   4. GitHub Actions Secrets in the CI/CD pipeline
#
# Variables WITHOUT a default MUST be supplied — Terraform will
# ask for them interactively if you forget.
# ============================================================

variable "aws_region" {
  description = "AWS region to deploy into. us-east-1 is cheapest for most services."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment name. Used in resource names and tags."
  type        = string
  default     = "production"

  # Validation block: reject nonsense values immediately rather than
  # discovering the problem after half the infrastructure is created.
  validation {
    condition     = contains(["production", "staging", "development"], var.environment)
    error_message = "Environment must be one of: production, staging, development."
  }
}

variable "db_username" {
  description = "PostgreSQL admin username. Do not use 'admin' or 'postgres' — AWS reserves those."
  type        = string
  default     = "featureflag_admin"
}

variable "db_password" {
  description = "PostgreSQL admin password. Must be at least 16 characters."
  type        = string
  sensitive   = true  # Marks this value as secret — Terraform won't print it in plan output
  # No default — you MUST supply this value. Never hardcode a password here.
}

variable "db_name" {
  description = "Name of the PostgreSQL database to create."
  type        = string
  default     = "featureflag"
}

variable "api_image" {
  description = <<-EOT
    Docker image to run in ECS.
    Format: ghcr.io/YOUR_GITHUB_USERNAME/featureflag/api:SHA
    This is injected by the GitHub Actions CD pipeline using the commit SHA.
  EOT
  type        = string
  default     = "ghcr.io/your-username/featureflag/api:latest"
}

variable "jwt_secret" {
  description = "Secret key for signing JWT tokens. Must be at least 32 characters. Generate with: openssl rand -hex 32"
  type        = string
  sensitive   = true  # Never printed in logs or plan output
}

variable "api_task_cpu" {
  description = "CPU units for the ECS API task. 256 = 0.25 vCPU. Minimum for Fargate."
  type        = number
  default     = 256
}

variable "api_task_memory" {
  description = "Memory in MB for the ECS API task. 512MB is the minimum for Spring Boot."
  type        = number
  default     = 512
}

variable "api_desired_count" {
  description = "Number of API task replicas to run. Minimum 2 for high availability."
  type        = number
  default     = 2
}

variable "redis_node_type" {
  description = <<-EOT
    ElastiCache Redis node size.
    cache.t3.micro is eligible for AWS Free Tier (750 hours/month).
    Upgrade to cache.t3.small or larger for production load.
  EOT
  type        = string
  default     = "cache.t3.micro"
}

variable "rds_instance_class" {
  description = <<-EOT
    RDS PostgreSQL instance size.
    db.t3.micro is eligible for AWS Free Tier (750 hours/month).
    Upgrade to db.t3.small or larger for production load.
  EOT
  type        = string
  default     = "db.t3.micro"
}
