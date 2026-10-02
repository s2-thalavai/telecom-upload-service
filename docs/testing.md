# Testing Strategy

There are two layers. Unit tests are fast, isolated, and need no Docker. Integration tests run a real server, real HTTP, a real filesystem, and a real database.

| Layer | Naming | Plugin | Command | What runs |
|---|---|---|---|---|
| Unit & web slice | `*Test.java` | Surefire | `make test` / `mvn test` | JUnit 5 + Mockito + `@WebMvcTest` |
| Integration | `*IT.java` | Failsafe | `make it` / `mvn verify` | `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` |
| All + coverage | | JaCoCo | `make verify` | report at `target/site/jacoco/index.html` |

## Unit tests

| Class | Kind | Covers |
|---|---|---|
| `FileTypeDetectorTest` | plain JUnit | PDF/PNG/JPEG signatures, unknown/short input, stream not consumed by detection |
| `FilenameSanitizerTest` | parameterized | path traversal (`../`, `C:\`), unsafe and control characters, fallback, 255-char truncation |
| `CsvLineParserTest` | plain JUnit | quoted commas, escaped quotes, empty fields |
| `FileSystemStorageServiceTest` | `@TempDir` | SHA-256 and size, unique keys, **mid-stream abort at limit leaves no partial file**, empty input, I/O failure clean-up, traversal-safe keys |
| `CustomerDocumentServiceTest` | Mockito | detected type beats client Content-Type, disguised executable never stored, **rollback deletes stored file**, delete only **after commit**, batch limits, early stream size check |
| `CustomerImportServiceTest` | Mockito + real Validator | batching (`saveAll` count), per-row errors with line numbers, error cap, duplicates (in-file, case-insensitive, vs DB), BOM header, wrong header |
| `CustomerServiceTest` | Mockito | create, duplicate email/msisdn, not found |
| `CustomerDocumentControllerTest` | `@WebMvcTest` | multipart binding, JSON `@RequestPart`, raw `InputStream`, 400/404/413/415 mapping, download headers (ETag, Content-Disposition, Content-Length) |
| `CustomerControllerTest`, `CustomerImportControllerTest` | `@WebMvcTest` | validation errors, status codes, JSON shape |

The transaction clean-up logic is unit-tested without a database. The test calls `TransactionSynchronizationManager.initSynchronization()`, runs the service, and then invokes `afterCompletion(STATUS_ROLLED_BACK)` or `afterCommit()` on the registered synchronizations.

## Integration tests

The `test` profile (`src/test/resources/application-test.properties`) uses **small limits**, so size rules can be exercised cheaply:

| Setting | Test value |
|---|---|
| `spring.servlet.multipart.max-file-size` | 1MB |
| `spring.servlet.multipart.max-request-size` | 3MB |
| `telecom.storage.max-stream-size` | 2MB |
| `telecom.storage.max-files-per-batch` | 3 |
| `telecom.import.batch-size` | 2 |

Storage goes to a temp directory set through `@DynamicPropertySource`. Every test starts with an empty database and an empty storage directory (`AbstractIntegrationTest.resetState`).

| Class | Covers |
|---|---|
| `DocumentUploadIT` | full round trip (upload → list → byte-identical download → delete removes the file); `Range` → 206; disguised exe → 415 with nothing stored; duplicate → 409 with the second file rolled back; **real Tomcat multipart limit → 413**; batch with JSON part; **batch all-or-nothing** (first valid file removed when the second is rejected); streaming bypasses the multipart limit; stream over limit → 413 |
| `CustomerImportIT` | mixed valid/invalid CSV over HTTP, batch commits, DB duplicates, wrong header 400, non-CSV 415 |
| `MySqlFlywayIT` | **prod profile on MySQL 8.4 via Testcontainers**: Flyway V1+V2 applied, schema matches JPA (`ddl-auto=none`), upload/download on MySQL. Skipped automatically without Docker |

## Running a single test

```bash
mvn test -Dtest=CustomerDocumentServiceTest
mvn test -Dtest='FilenameSanitizerTest#sanitizesPathsAndUnsafeCharacters'
mvn verify -Dtest=SkipUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=DocumentUploadIT
```

Reports land in `target/surefire-reports` (unit) and `target/failsafe-reports` (integration).
