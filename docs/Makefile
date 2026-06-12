# ============================================================
# Makefile — Shortcut commands for the FeatureFlag project
# ============================================================
# Usage: make <target>
# Run "make help" to see all available commands.
# ============================================================

.PHONY: help start start-scaled stop test test-integration load-test \
        logs redis-cli clean tf-init tf-plan tf-apply tf-destroy sdk-publish

# Default target: show help
help:
	@echo ""
	@echo "  FeatureFlag Service — Available commands"
	@echo "  ─────────────────────────────────────────"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""

# ─────────────────────────────────────────────────────────────
# Docker Compose — Local Development
# ─────────────────────────────────────────────────────────────

start: ## Start all 5 services (api, frontend, redis, postgres, nginx)
	docker compose up -d
	@echo ""
	@echo "  Services started"
	@echo "  Admin dashboard:  http://localhost"
	@echo "  Redis Commander:  http://localhost:8001"
	@echo "  API health:       http://localhost:8080/actuator/health"
	@echo ""

start-scaled: ## Start with 3 API instances (for cache invalidation demo)
	docker compose up -d --scale api=3
	@echo ""
	@echo "  Started with 3 API instances"
	@echo "  Open Redis Commander and update a flag to see"
	@echo "  all 3 cache keys disappear simultaneously."
	@echo ""

stop: ## Stop all running containers
	docker compose down

logs: ## Tail logs from all services (Ctrl+C to stop)
	docker compose logs -f

logs-api: ## Tail API logs only
	docker compose logs -f api

# ─────────────────────────────────────────────────────────────
# Testing
# ─────────────────────────────────────────────────────────────

test: ## Run unit tests (no Docker required, ~10 seconds)
	./mvnw test -pl api,sdk

test-integration: ## Run Testcontainers integration tests (~90 seconds)
	@echo "Starting integration tests — Docker must be running..."
	./mvnw verify -pl api -Pintegration-tests

test-all: test test-integration ## Run ALL tests (unit + integration)

load-test: ## Run k6 load test (requires: docker compose up -d AND k6 installed)
	@which k6 > /dev/null || (echo " k6 not found. Install: https://k6.io/docs/getting-started/installation" && exit 1)
	@echo "Running load test — make sure 'make start' has been run first"
	k6 run tests/load/flag-evaluation.js

# ─────────────────────────────────────────────────────────────
# Redis
# ─────────────────────────────────────────────────────────────

redis-cli: ## Open an interactive Redis CLI session inside the container
	docker compose exec redis redis-cli

redis-keys: ## List all current Redis keys (useful for debugging cache state)
	docker compose exec redis redis-cli KEYS "*"

redis-flush: ##   Delete ALL Redis keys (clears the entire cache)
	@read -p "This deletes all cached data. Are you sure? [y/N] " confirm && \
	  [ "$${confirm}" = "y" ] && docker compose exec redis redis-cli FLUSHALL || echo "Aborted."

# ─────────────────────────────────────────────────────────────
# SDK
# ─────────────────────────────────────────────────────────────

sdk-build: ## Build and install the SDK locally (without publishing)
	./mvnw install -pl sdk -am

sdk-publish: ## Publish SDK to GitHub Packages (requires GITHUB_TOKEN env var)
	@test -n "$(GITHUB_TOKEN)" || (echo " GITHUB_TOKEN is not set" && exit 1)
	./mvnw deploy -pl sdk

# ─────────────────────────────────────────────────────────────
# Terraform — AWS Infrastructure
# ─────────────────────────────────────────────────────────────
# These commands operate on the /infrastructure directory.
# Requires: Terraform 1.6+, AWS CLI configured with credentials.

TF_DIR = infrastructure

tf-init: ## Initialize Terraform (run once, or after adding providers)
	cd $(TF_DIR) && terraform init

tf-fmt: ## Auto-format all Terraform files
	cd $(TF_DIR) && terraform fmt -recursive

tf-validate: ## Validate Terraform configuration (syntax check)
	cd $(TF_DIR) && terraform validate

tf-plan: ## Preview what Terraform will create/change (no changes made)
	@test -n "$(TF_VAR_db_password)" || (echo " Set TF_VAR_db_password first" && exit 1)
	@test -n "$(TF_VAR_jwt_secret)"  || (echo " Set TF_VAR_jwt_secret first"  && exit 1)
	cd $(TF_DIR) && terraform plan

tf-apply: ## Create or update AWS infrastructure
	@test -n "$(TF_VAR_db_password)" || (echo " Set TF_VAR_db_password first" && exit 1)
	@test -n "$(TF_VAR_jwt_secret)"  || (echo " Set TF_VAR_jwt_secret first"  && exit 1)
	@echo "  This will create AWS resources that may incur costs."
	@read -p "Continue? [y/N] " confirm && [ "$${confirm}" = "y" ] || exit 1
	cd $(TF_DIR) && terraform apply

tf-output: ## Print Terraform outputs (ALB URL, Redis endpoint, etc.)
	cd $(TF_DIR) && terraform output

tf-destroy: ##   DESTROY all AWS infrastructure created by Terraform
	@echo "  WARNING: This permanently deletes all AWS resources."
	@read -p "Type 'destroy' to confirm: " confirm && [ "$${confirm}" = "destroy" ] || exit 1
	cd $(TF_DIR) && terraform destroy

# ─────────────────────────────────────────────────────────────
# Housekeeping
# ─────────────────────────────────────────────────────────────

clean: ## Stop containers and DELETE all volumes (wipes DB and Redis data)
	@echo "  This deletes all local database and Redis data."
	@read -p "Are you sure? [y/N] " confirm && \
	  [ "$${confirm}" = "y" ] && docker compose down -v || echo "Aborted."

clean-build: ## Remove Maven build artifacts
	./mvnw clean

build: ## Build all Maven modules (api + sdk)
	./mvnw package -DskipTests

lint: ## Run Checkstyle lint on all Java code
	./mvnw checkstyle:check
