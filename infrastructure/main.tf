# ============================================================
# main.tf — Provider configuration + Terraform state backend
# ============================================================
#
# This file answers three questions for Terraform:
#   1. Which cloud provider are we using? (AWS)
#   2. Which region? (configurable via variable)
#   3. Where does Terraform store its own state?
#
# WHY REMOTE STATE?
# Terraform keeps track of what it has created in a "state file".
# By default this lives on your laptop (terraform.tfstate).
# If two developers run terraform apply at the same time from
# different laptops, they both have different state files and
# will fight each other — corrupting your infrastructure.
# Storing state in S3 with a DynamoDB lock table solves this:
# only one developer can run apply at a time, and all developers
# see the same picture of what exists.
# ============================================================

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0" # Pin to major version to avoid breaking changes
    }
  }

  # Remote state in S3 — prevents developers from overwriting each other.
  # Before running terraform init, manually create this bucket and table,
  # OR comment this block out and use local state for development.
  #
  # Create S3 bucket:
  #   aws s3api create-bucket --bucket featureflag-tf-state --region us-east-1
  # Create DynamoDB table (for state locking):
  #   aws dynamodb create-table \
  #     --table-name featureflag-tf-locks \
  #     --attribute-definitions AttributeName=LockID,AttributeType=S \
  #     --key-schema AttributeName=LockID,KeyType=HASH \
  #     --billing-mode PAY_PER_REQUEST
  backend "s3" {
    bucket         = "featureflag-tf-state"       # S3 bucket that stores the state file
    key            = "prod/terraform.tfstate"      # Path within the bucket
    region         = "us-east-1"
    dynamodb_table = "featureflag-tf-locks"        # DynamoDB table for locking
    encrypt        = true                          # Encrypt state at rest (contains passwords)
  }
}

# ============================================================
# AWS Provider
# ============================================================
# The "provider" block is like telling Terraform which tool to use.
# AWS provider reads credentials from environment variables:
#   AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# These should never be hardcoded here. Set them via:
#   export AWS_ACCESS_KEY_ID=...
#   export AWS_SECRET_ACCESS_KEY=...
# Or use GitHub Actions Secrets (see cd.yml)
provider "aws" {
  region = var.aws_region

  # Tag every AWS resource with these labels automatically.
  # This makes billing reports readable ("which resource is this?")
  # and is a standard practice at every serious company.
  default_tags {
    tags = {
      Project     = "featureflag"
      Environment = var.environment
      ManagedBy   = "terraform"       # Tells the team: don't touch this in the console
    }
  }
}
