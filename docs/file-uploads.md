# File Uploads – Cheat Sheet

## Wire formats

| Format | Content-Type | Spring binding | Use |
|---|---|---|---|
| Multipart | `multipart/form-data` | `MultipartFile` via `@RequestParam` / `@RequestPart` | browsers, files + fields, several files |
| Raw body | `application/octet-stream`, `application/pdf`, … | `InputStream` parameter | very large files, machine-to-machine |

## Limits (all layers must agree)

| Layer | Setting | Here |
|---|---|---|
| Spring multipart | `spring.servlet.multipart.max-file-size` / `max-request-size` | 20MB / 60MB |
| Spring multipart | `file-size-threshold` (memory vs temp file) | 512KB |
| Tomcat | `server.tomcat.max-swallow-size` (clean 413 instead of reset) | 60MB |
| App (stream) | `telecom.storage.max-stream-size` (enforced mid-stream) | 200MB |
| App (batch) | `telecom.storage.max-files-per-batch` | 10 |
| ingress-nginx | `nginx.ingress.kubernetes.io/proxy-body-size` (default 1MB!) | 250m |

## Status codes

| Situation | Status |
|---|---|
| Missing `file` part, empty file, bad CSV header, too many files | 400 |
| Unknown customer / document | 404 |
| Same file twice for a customer (SHA-256) | 409 |
| Over a size limit (`MaxUploadSizeExceededException` / stream limit) | 413 |
| Bytes are not PDF/PNG/JPEG, wrong endpoint Content-Type | 415 |

## Design rules in this code base

1. Never call `MultipartFile.getBytes()`. Stream with `getInputStream()` and a 64KB buffer.
2. Never use the client filename as a path. Storage keys are `UUID + extension`, and display names are sanitized.
3. Never trust the client `Content-Type`. The type is detected from magic bytes against an allow-list.
4. Compute SHA-256 while streaming. It's used for dedup, the ETag, and integrity checks.
5. Write to `<key>.part`, then do an atomic move, so a half-written file is never visible.
6. Keep bytes and rows consistent. On rollback, the new file is deleted. On delete, the file is removed only after commit.
7. Commit CSV imports in batches and report row errors instead of failing the whole file.
