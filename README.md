# Telecom Upload Service

A Spring Boot 3.5 / Java 21 REST API for telecom KYC documents and bulk customer onboarding.
It demonstrates multipart uploads, raw streaming uploads, magic-byte validation, checksums,
transaction-safe file storage, and CSV import, with unit and integration tests.

| | |
|---|---|
| Stack | Spring Web, Spring Data JPA, Bean Validation, Flyway, Actuator |
| Storage | Local filesystem behind a `StorageService` interface (swap for S3 / Azure Blob) |
| Databases | H2 (dev, tests), MySQL 8.4 (prod, Testcontainers IT) |
| Tests | JUnit 5, Mockito, MockMvc slices, TestRestTemplate on real Tomcat, Testcontainers, JaCoCo |
| Infra | Dockerfile, Docker Compose, Kubernetes (kustomize), Makefile, GitHub Actions |

## Quick start

```bash
make run        # dev profile -> http://localhost:8080 (KYC desk UI)
make test       # unit + slice tests (fast)
make verify     # all tests + coverage report
make up         # Docker Compose: MySQL + app
make smoke      # end-to-end checks with curl
```

## Endpoints

| Method | Path | Body |
|---|---|---|
| `POST` | `/api/v1/customers/{id}/documents` | multipart: `file`, `type`, `description?` |
| `POST` | `/api/v1/customers/{id}/documents/batch` | multipart: `metadata` (JSON) + `files` (repeated) |
| `POST` | `/api/v1/customers/{id}/documents/stream?type=` | raw PDF/PNG/JPEG/octet-stream, `X-Filename` header |
| `GET` | `/api/v1/customers/{id}/documents` | list |
| `GET` | `/api/v1/customers/{id}/documents/{docId}` | download (supports `Range`) |
| `DELETE` | `/api/v1/customers/{id}/documents/{docId}` | 204 |
| `POST` | `/api/v1/customers/import` | multipart CSV `file` |
| `GET` / `POST` | `/api/v1/customers` | customers (JSON) |

See [HOW_TO_RUN.md](HOW_TO_RUN.md), [docs/testing.md](docs/testing.md), [docs/file-uploads.md](docs/file-uploads.md) and [CLAUDE.md](CLAUDE.md).
