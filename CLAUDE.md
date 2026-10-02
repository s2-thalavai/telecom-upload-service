# CLAUDE.md

Context for Claude Code (and other AI assistants) working in this repository.

## What this is

`telecom-upload-service` is a Spring Boot 3.5 / Java 21 REST API for telecom customers' KYC documents
(multipart, batch and raw streaming uploads) and bulk customer onboarding from CSV. Upload safety rules
(limits, type detection, path safety, DB/file consistency) are **features under test**. Do not weaken them casually.

## Commands

| Task | Command |
|---|---|
| Run (H2 file DB, `./data/uploads`) | `make run` |
| Unit + slice tests (fast, no Docker) | `make test` |
| Integration tests (`*IT`) | `make it` |
| Everything + coverage | `make verify` → `target/site/jacoco/index.html` |
| Compose (MySQL + app) | `make up` / `make logs` / `make down` / `make nuke` |
| End-to-end curl checks | `make smoke` |
| Kubernetes | `make k8s-deploy` / `make k8s-port-forward` / `make k8s-delete` |

Run `make test` after any change, and `make verify` before declaring work done.

## Layout

```
src/main/java/com/telecom/
  config/      StorageProperties, ImportProperties (@ConfigurationProperties records), DataSeeder (dev only)
  controller/  CustomerController, CustomerDocumentController, CustomerImportController
  dto/         records (requests, responses, ImportResult)
  entity/      Customer, CustomerDocument (metadata only), enums
  exception/   domain exceptions + GlobalExceptionHandler (ProblemDetail)
  repository/  Spring Data JPA
  service/     CustomerService, CustomerDocumentService, CustomerImportService
  storage/     StorageService interface + FileSystemStorageService
  util/        FileTypeDetector (magic bytes), FilenameSanitizer, CsvLineParser
src/main/resources/  application{,-dev,-prod}.properties, db/migration (Flyway, prod), static/ (KYC desk UI)
src/test/java/com/telecom/
  util|storage|service|controller/  unit + @WebMvcTest slices   (*Test, Surefire)
  integration/                      @SpringBootTest + real Tomcat  (*IT, Failsafe)
  support/TestFiles                 byte payloads with real magic numbers
```

## Invariants (keep them true, and keep their tests passing)

1. **No full buffering.** Never `MultipartFile.getBytes()` or `readAllBytes()` on uploads in main code. Stream through `StorageService.store`.
2. **Client filenames are display-only.** Storage keys are server-generated. Always pass names through `FilenameSanitizer`.
3. **Type comes from bytes.** `FileTypeDetector` + `telecom.storage.allowed-content-types`. Adding a type means adding a signature, an extension mapping, and tests.
4. **Consistency.** New files are registered for deletion on rollback (`deleteFileUnlessCommitted`). Deletes remove the file in `afterCommit`. Any new write path must do the same.
5. **Limits.** The multipart limits apply to multipart only. The stream endpoint enforces `max-stream-size` itself (Content-Length pre-check plus mid-stream abort). If you change limits, update the ingress `proxy-body-size` and `server.tomcat.max-swallow-size` too.
6. **Errors** are `ProblemDetail`: 400 invalid, 404 missing, 409 duplicate, 413 too large, 415 wrong type, 500 storage.

## Database

- **dev**: H2 *file* DB (`./data/telecomdb`), `ddl-auto=update`, Flyway off.
- **test**: H2 in-memory, `create-drop`, small limits (see `application-test.properties`).
- **prod**: MySQL 8.4. **Flyway owns the schema** (`ddl-auto=none`). Entity changes need a new `V<n>__*.sql`, and `MySqlFlywayIT` verifies it. Never edit an applied migration.

## Testing conventions

- Unit tests: `*Test`, no Spring context unless it's a `@WebMvcTest` slice. Mock with `@Mock` / `@MockitoBean`.
- Integration tests: `*IT`, extend `AbstractIntegrationTest` (gives real HTTP, temp storage, clean state).
- Use `TestFiles` for payloads (real magic bytes). Do not use random bytes for "PDFs".
- Mockito strict stubs are on. Use answer-based stubs when a method is called with several arguments.
- Assert both sides of consistency: DB rows (`documents.count()`) **and** files (`storedFileCount()`).

## Deployment notes

- The image runs as UID 10001. Uploads go to `/app/data/uploads` (a volume), and temp files go to `/app/tmp`.
- K8s uses a ReadWriteOnce PVC for uploads, which means **1 replica with the Recreate strategy**. To scale out, use RWX storage or an object-store `StorageService`.
- The ingress sets `proxy-body-size: 250m` and `proxy-request-buffering: off`.
- `k8s/02-secret.yaml` contains demo values only. Never commit real secrets. `.env` is git-ignored.
