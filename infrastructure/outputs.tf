# ============================================================
# outputs.tf — Values Printed After `terraform apply`
# ============================================================
#
# Outputs are like the "return value" of your Terraform run.
# After "terraform apply" finishes, Terraform prints these values.
# They're also useful when one Terraform module needs to reference
# another module's resources.
#
# You can query outputs at any time with: terraform output
# Or a specific one: terraform output alb_dns_name
# ============================================================

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer. Point your domain's CNAME here."
  value       = aws_lb.main.dns_name
  # Example: featureflag-alb-1234567890.us-east-1.elb.amazonaws.com
  # In production you'd create a Route53 record pointing your domain to this.
}

output "ecs_cluster_name" {
  description = "ECS cluster name. Use this in deploy scripts: aws ecs update-service --cluster <name>"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name. Used in GitHub Actions CD pipeline for rolling deploys."
  value       = aws_ecs_service.api.name
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint. Used as SPRING_REDIS_HOST in the ECS task."
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  # Example: featureflag-redis.abc123.0001.use1.cache.amazonaws.com
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint hostname. Used to build the SPRING_DATASOURCE_URL."
  value       = aws_db_instance.postgres.address
  # Example: featureflag-postgres.abc123.us-east-1.rds.amazonaws.com
  # Full JDBC URL: jdbc:postgresql://<endpoint>:5432/featureflag
}

output "rds_port" {
  description = "RDS PostgreSQL port (always 5432 for PostgreSQL)."
  value       = aws_db_instance.postgres.port
}

output "vpc_id" {
  description = "VPC ID. Useful if you need to add more resources to the same network."
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs. Useful for adding more services to the private network."
  value       = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

output "deploy_command" {
  description = "Copy-paste command to trigger a rolling deploy after pushing a new image."
  value       = <<-EOT
    aws ecs update-service \
      --cluster ${aws_ecs_cluster.main.name} \
      --service ${aws_ecs_service.api.name} \
      --force-new-deployment \
      --region ${var.aws_region}
  EOT
}
