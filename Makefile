# Telecom upload service – common tasks. Run `make` or `make help`.
APP_NAME  := telecom-upload-service
VERSION   := 1.0.0
IMAGE     := $(APP_NAME):$(VERSION)
MVN       ?= mvn
KUBECTL   ?= kubectl
BASE_URL  ?= http://localhost:8080
NAMESPACE := telecom-upload

.DEFAULT_GOAL := help
.PHONY: help build test it verify coverage run clean docker-build docker-run env-file up down nuke logs ps \
	    tools smoke db-shell db-reports orphans k8s-deploy k8s-status k8s-logs k8s-port-forward k8s-delete

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

# ---------------- Build & tests ----------------
build: ## Package the jar (skip tests)
	$(MVN) -B clean package -DskipTests

test: ## Unit + web-slice tests only (*Test.java, fast, no Docker)
	$(MVN) -B test

it: ## Integration tests only (*IT.java; MySQL IT runs if Docker is available)
	$(MVN) -B verify -Dtest=SkipUnitTests -Dsurefire.failIfNoSpecifiedTests=false

verify: ## Everything: unit + integration tests + coverage report
	$(MVN) -B clean verify

coverage: verify ## Run all tests and print the coverage report location
	@echo "Coverage report: target/site/jacoco/index.html"

run: ## Run locally with the dev profile (H2 file DB, ./data/uploads)
	./scripts/run-dev.sh

clean: ## Remove build output and local dev data
	$(MVN) -B clean
	rm -rf data

# ---------------- Docker ----------------
docker-build: ## Build the Docker image
	docker build -t $(IMAGE) .

docker-run: docker-build ## Run the image alone on the dev profile (H2)
	docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev $(IMAGE)

env-file: ## Create .env from .env.example if missing
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

up: env-file ## Start MySQL + app with Docker Compose (prod profile)
	docker compose up -d --build
	@echo "App: $(BASE_URL)   (first start ~60s; 'make logs' to follow)"

down: ## Stop Compose (keeps DB and upload volumes)
	docker compose down

nuke: ## Stop Compose and delete DB + upload volumes
	docker compose --profile tools down -v

logs: ## Follow app logs
	docker compose logs -f app

ps: ## Show Compose services
	docker compose ps

tools: env-file ## Start Adminer DB UI on http://localhost:8081
	docker compose --profile tools up -d adminer

smoke: ## End-to-end upload checks against BASE_URL
	BASE_URL=$(BASE_URL) ./scripts/smoke-test.sh

db-shell: ## MySQL shell in the Compose DB
	./scripts/db-shell.sh

db-reports: ## Run sql/reports.sql against the Compose DB
	docker compose exec -T mysql sh -c 'mysql -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" "$$MYSQL_DATABASE"' < sql/reports.sql

orphans: ## List stored files without DB rows (and vice versa) in Compose
	./scripts/find-orphans.sh

# ---------------- Kubernetes ----------------
k8s-deploy: ## Build, load into local cluster, apply k8s/ manifests
	./scripts/k8s-deploy.sh

k8s-status: ## Pods, services, PVCs, ingress
	$(KUBECTL) -n $(NAMESPACE) get pods,svc,pvc,ingress

k8s-logs: ## Follow app logs in Kubernetes
	$(KUBECTL) -n $(NAMESPACE) logs -f deployment/$(APP_NAME)

k8s-port-forward: ## Forward localhost:8080 to the service
	$(KUBECTL) -n $(NAMESPACE) port-forward svc/$(APP_NAME) 8080:80

k8s-delete: ## Delete all K8s resources including PVCs (data lost)
	./scripts/k8s-teardown.sh
