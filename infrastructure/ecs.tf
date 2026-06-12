# ============================================================
# ecs.tf — ECS Fargate Cluster + API Service
# ============================================================
#
# CONCEPT: WHAT IS ECS FARGATE?
#
# ECS (Elastic Container Service) is AWS's managed Docker platform.
# When you run "docker compose up" locally, Docker starts your containers
# on your laptop. ECS does the same thing, but:
#   - In the cloud, across multiple servers
#   - With automatic restarts if a container crashes
#   - With health checks that replace unhealthy containers
#   - With scaling (start more containers when traffic is high)
#
# FARGATE specifically means you don't manage the underlying servers.
# You say "I need a container with 0.25 vCPU and 512MB RAM", and AWS
# finds a server to run it on. You pay per second of usage.
# This is in contrast to ECS on EC2, where you manage the servers yourself.
#
# The mapping from our docker-compose.yml:
#   docker-compose service "api"  →  ECS Task Definition "featureflag-api"
#   docker compose up             →  aws ecs update-service
#   docker compose ps             →  aws ecs list-tasks
# ============================================================

# ============================================================
# IAM Roles — Permission Slips for AWS Resources
# ============================================================
#
# IAM (Identity and Access Management) is AWS's permission system.
# Every AWS resource that needs to call another AWS service needs
# an IAM role. Think of it as the resource's ID badge.
#
# We need TWO roles:
#   1. Task Execution Role: allows ECS itself to pull Docker images
#      from GHCR/ECR and write logs to CloudWatch. ECS uses this.
#   2. Task Role: allows YOUR APPLICATION CODE inside the container
#      to call AWS APIs (S3, SSM for secrets, etc.). Your code uses this.

# Task Execution Role — used by ECS to start the container
resource "aws_iam_role" "ecs_task_execution" {
  name = "featureflag-ecs-execution-role"

  # "assume_role_policy" says: which AWS service is allowed to USE this role?
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }  # ECS can assume this role
      Action    = "sts:AssumeRole"
    }]
  })
}

# Attach AWS's managed policy that gives ECS everything it needs to start containers:
# pull from ECR, write to CloudWatch Logs, read Secrets Manager.
resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Task Role — used by your application code
resource "aws_iam_role" "ecs_task" {
  name = "featureflag-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# ============================================================
# CloudWatch Log Group — Where Container Logs Go
# ============================================================
# Every System.out.println() and @Slf4j log statement from your
# Spring Boot app will appear here. CloudWatch is like AWS's
# centralized log aggregator.

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/featureflag-api"
  retention_in_days = 14  # Keep logs for 2 weeks, then auto-delete (controls cost)
}

# ============================================================
# ECS Cluster
# ============================================================
# A cluster is a logical grouping of tasks and services.
# Think of it as the "docker-compose project" namespace.
# Container Insights gives you CPU/memory metrics in CloudWatch.

resource "aws_ecs_cluster" "main" {
  name = "featureflag-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"  # Enables CPU/memory/network metrics per task
  }

  tags = { Name = "featureflag-cluster" }
}

# ============================================================
# Task Definition — The "docker-compose.yml" for ECS
# ============================================================
# A Task Definition is the blueprint for a single running unit.
# It answers: which image? how much CPU/RAM? what env vars?
# what ports? where do logs go? which IAM role?
#
# This maps DIRECTLY to the "api" service in our docker-compose.yml.

resource "aws_ecs_task_definition" "api" {
  family                   = "featureflag-api"
  requires_compatibilities = ["FARGATE"]  # Serverless containers
  network_mode             = "awsvpc"     # Each task gets its own network interface (required for Fargate)
  cpu                      = var.api_task_cpu     # 256 = 0.25 vCPU
  memory                   = var.api_task_memory  # 512 MB
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn  # Used by ECS to start us
  task_role_arn            = aws_iam_role.ecs_task.arn            # Used by our app code

  # Container definitions — equivalent to a docker-compose service definition.
  # We use jsonencode() rather than a separate JSON file so that Terraform
  # can interpolate variables (redis_endpoint, db_url) directly.
  container_definitions = jsonencode([
    {
      name      = "api"
      image     = var.api_image  # Injected by GitHub Actions with the commit SHA
      essential = true           # If this container dies, restart the whole task

      portMappings = [
        {
          containerPort = 8080  # Spring Boot listens on 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        # These reference Terraform outputs from other files (redis.tf, rds.tf)
        { name = "SPRING_PROFILES_ACTIVE",   value = "prod" },
        { name = "SPRING_REDIS_HOST",        value = aws_elasticache_cluster.redis.cache_nodes[0].address },
        { name = "SPRING_REDIS_PORT",        value = "6379" },
        { name = "SPRING_DATASOURCE_URL",    value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_name}" },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username }
      ]

      secrets = [
        # Secrets are stored in AWS Secrets Manager / SSM Parameter Store.
        # They are injected at container start time — not stored in the image.
        # Never put passwords in the "environment" block above.
        #
        # To create these secrets:
        #   aws ssm put-parameter --name /featureflag/db-password --type SecureString --value "yourpassword"
        #   aws ssm put-parameter --name /featureflag/jwt-secret --type SecureString --value "yourjwtsecret"
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "arn:aws:ssm:${var.aws_region}:ACCOUNT_ID:parameter/featureflag/db-password" },
        { name = "JWT_SECRET",                 valueFrom = "arn:aws:ssm:${var.aws_region}:ACCOUNT_ID:parameter/featureflag/jwt-secret" }
      ]

      healthCheck = {
        # ECS uses this to decide if the container is ready for traffic.
        # Same endpoint our Docker health check uses — Spring Actuator.
        command     = ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
        interval    = 30  # Check every 30 seconds
        timeout     = 10  # Fail if no response in 10 seconds
        retries     = 3   # Replace task after 3 consecutive failures
        startPeriod = 60  # Grace period: Spring Boot needs ~45s to start
      }

      logConfiguration = {
        logDriver = "awslogs"  # Send stdout/stderr to CloudWatch
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "api"  # Log stream will be: api/api/TASK_ID
        }
      }
    }
  ])

  tags = { Name = "featureflag-api-task" }
}

# ============================================================
# ECS Service — Keeps N Copies of the Task Running
# ============================================================
# The Service is what actually RUNS the Task Definition.
# It answers: how many replicas? which load balancer? which subnets?
#
# This maps to "docker compose up --scale api=2"

resource "aws_ecs_service" "api" {
  name            = "featureflag-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn  # Which blueprint to use
  desired_count   = var.api_desired_count             # How many replicas (default: 2)
  launch_type     = "FARGATE"

  # Network configuration: run tasks in PRIVATE subnets.
  # They receive traffic from the ALB, not from the internet directly.
  network_configuration {
    subnets          = [aws_subnet.private_a.id, aws_subnet.private_b.id]
    security_groups  = [aws_security_group.ecs_api.id]
    assign_public_ip = false  # Private subnet — no public IP
  }

  # Connect this service to the ALB target group.
  # The ALB will route traffic to these tasks and health-check them.
  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"   # Must match the container name in the task definition
    container_port   = 8080
  }

  # Deployment strategy: rolling update.
  # "minimum_healthy_percent = 50" means during a deploy, AWS keeps at least 1
  # of your 2 tasks running. No downtime.
  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200  # Allows AWS to start new tasks before killing old ones

  # Wait for the service to stabilize before marking the deploy successful.
  # Without this, "terraform apply" might return success while tasks are still starting.
  wait_for_steady_state = true

  # Terraform should not try to re-set desired_count if auto-scaling has changed it
  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [
    aws_lb_listener.http,               # ALB must exist before the service
    aws_iam_role_policy_attachment.ecs_execution_policy  # IAM must be attached
  ]

  tags = { Name = "featureflag-api-service" }
}
