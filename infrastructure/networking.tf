# ============================================================
# networking.tf — VPC, Subnets, Security Groups
# ============================================================
#
# CONCEPT: WHY DO WE NEED THIS?
#
# In AWS, every resource lives inside a network called a VPC
# (Virtual Private Cloud). Think of it as a private office building:
#   - The VPC is the building
#   - Subnets are floors
#   - Security Groups are the locked doors on each floor
#   - The Internet Gateway is the building's front door
#
# PUBLIC subnets: resources here can receive traffic from the internet.
#   → Our Load Balancer lives here.
#
# PRIVATE subnets: resources here CANNOT be reached from the internet.
#   → Our ECS tasks, RDS, and ElastiCache live here.
#   → They can only be reached from the Load Balancer or each other.
#   → This is a critical security boundary.
#
# WHY TWO AVAILABILITY ZONES?
# AWS regions have multiple physical data centers (AZs).
# Spreading resources across two AZs means: if one data center
# has a power outage, your app keeps running in the other one.
# This is the minimum for high availability.
# ============================================================

# ============================================================
# VPC — The Private Network
# ============================================================
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"  # 65,536 private IP addresses
  enable_dns_hostnames = true            # Resources get readable DNS names
  enable_dns_support   = true

  tags = { Name = "featureflag-vpc" }
}

# ============================================================
# Internet Gateway — The Building's Front Door
# ============================================================
# Without this, nothing inside the VPC can reach the internet.
# Public subnets route traffic through this gateway.
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id  # Attach to our VPC

  tags = { Name = "featureflag-igw" }
}

# ============================================================
# Public Subnets — Where the Load Balancer Lives
# ============================================================
# We need two public subnets in different AZs for the ALB
# (Application Load Balancer). AWS requires this.

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"       # 256 addresses
  availability_zone       = "${var.aws_region}a" # e.g., us-east-1a
  map_public_ip_on_launch = true                 # Resources here get a public IP

  tags = { Name = "featureflag-public-a", Tier = "public" }
}

resource "aws_subnet" "public_b" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.aws_region}b" # e.g., us-east-1b
  map_public_ip_on_launch = true

  tags = { Name = "featureflag-public-b", Tier = "public" }
}

# ============================================================
# Private Subnets — Where ECS, RDS, and Redis Live
# ============================================================
# Resources here have NO direct internet access — a security boundary.
# They reach the internet (for package updates, etc.) via NAT Gateway
# if needed, but for this project we skip NAT Gateway to avoid cost.

resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.3.0/24"
  availability_zone = "${var.aws_region}a"

  tags = { Name = "featureflag-private-a", Tier = "private" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.4.0/24"
  availability_zone = "${var.aws_region}b"

  tags = { Name = "featureflag-private-b", Tier = "private" }
}

# ============================================================
# Route Table — Direct Public Subnet Traffic to the Internet
# ============================================================
# A route table is like a GPS: it says "traffic going to the internet
# (0.0.0.0/0) should go through the Internet Gateway".

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"                   # All external traffic
    gateway_id = aws_internet_gateway.main.id   # Goes through the internet gateway
  }

  tags = { Name = "featureflag-public-rt" }
}

# Associate the route table with BOTH public subnets
resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_b" {
  subnet_id      = aws_subnet.public_b.id
  route_table_id = aws_route_table.public.id
}

# ============================================================
# Security Groups — Locked Doors Between Services
# ============================================================
# Each security group is a firewall for a specific resource.
# We follow the principle of LEAST PRIVILEGE:
# Only allow the exact traffic that is needed. Deny everything else.

# --- Load Balancer Security Group ---
# Allow: HTTP (80) and HTTPS (443) from anyone on the internet
# Deny: Everything else
resource "aws_security_group" "alb" {
  name        = "featureflag-alb-sg"
  description = "Allow HTTP/HTTPS from the internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]  # Anyone on the internet
  }

  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic (to reach ECS tasks)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"          # -1 means all protocols
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "featureflag-alb-sg" }
}

# --- ECS (API) Security Group ---
# Allow: Port 8080 ONLY from the Load Balancer (not from the internet directly)
# Deny: Everything else
resource "aws_security_group" "ecs_api" {
  name        = "featureflag-ecs-api-sg"
  description = "Allow port 8080 from ALB only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "API port from ALB only — internet cannot hit this directly"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]  # ONLY from the load balancer
  }

  egress {
    description = "Allow all outbound (ECS needs to reach RDS, Redis, ECR)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "featureflag-ecs-api-sg" }
}

# --- Redis (ElastiCache) Security Group ---
# Allow: Port 6379 ONLY from ECS tasks
# Deny: Everything else, including the internet
resource "aws_security_group" "redis" {
  name        = "featureflag-redis-sg"
  description = "Allow Redis port from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Redis from ECS API only — never exposed to internet"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_api.id]
  }

  tags = { Name = "featureflag-redis-sg" }
}

# --- RDS (PostgreSQL) Security Group ---
# Allow: Port 5432 ONLY from ECS tasks
# Deny: Everything else
resource "aws_security_group" "rds" {
  name        = "featureflag-rds-sg"
  description = "Allow PostgreSQL port from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS API only — never exposed to internet"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_api.id]
  }

  tags = { Name = "featureflag-rds-sg" }
}

# ============================================================
# Application Load Balancer
# ============================================================
# The ALB is the entry point for all traffic.
# It sits in the public subnets and forwards requests to ECS tasks
# in the private subnets. This is the standard AWS architecture.

resource "aws_lb" "main" {
  name               = "featureflag-alb"
  internal           = false              # false = internet-facing
  load_balancer_type = "application"      # Layer 7 (HTTP/HTTPS)
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]

  # Access logs help you debug production issues.
  # Requires an S3 bucket configured to accept ALB logs.
  # enable_deletion_protection = true  # Uncomment in production

  tags = { Name = "featureflag-alb" }
}

# Target Group — the pool of ECS tasks the ALB distributes traffic to
resource "aws_lb_target_group" "api" {
  name        = "featureflag-api-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"  # Required for Fargate (tasks have IPs, not EC2 instance IDs)

  health_check {
    # The ALB will hit this endpoint every 30s to check if the task is healthy.
    # If it fails 3 times, the task is replaced. This is Spring Actuator.
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 10
  }

  tags = { Name = "featureflag-api-tg" }
}

# Listener — tells the ALB what to do with incoming traffic on port 80
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn  # Forward to our API tasks
  }
}
