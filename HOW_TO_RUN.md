# How to Run

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 | local run, tests |
| Maven | 3.9+ | local run, tests (or build inside Docker) |
| Docker + Compose v2 | recent | Compose stack, image, MySQL integration test |
| kubectl + minikube / kind / Docker Desktop | recent | Kubernetes |
| make, bash, curl | any | Makefile, scripts (Windows: WSL2 or Git Bash) |

There is no Maven wrapper bundled. Run `mvn -N wrapper:wrapper` once if you want `./mvnw`.

---

## 1. Local (dev profile)

```bash
make run          # or: mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- KYC desk UI: http://localhost:8080
- API: http://localhost:8080/api/v1/customers
- H2 console: http://localhost:8080/h2-console. Use JDBC URL `jdbc:h2:file:./data/telecomdb;AUTO_SERVER=TRUE`, user `sa`, and an empty password.
- Uploaded files are stored in `./data/uploads`. `make clean` wipes `./data`.

## 2. Tests

```bash
make test         # unit + @WebMvcTest slices       (mvn test)
make it           # integration tests only          (*IT)
make verify       # all tests + JaCoCo coverage     (mvn clean verify)
```

`MySqlFlywayIT` starts MySQL 8.4 with Testcontainers and is **skipped when Docker isn't running**.
See [docs/testing.md](docs/testing.md) for what each test covers and how to run a single test.

## 3. Docker Compose (MySQL + app, prod profile)

```bash
make up           # creates .env, builds the image, starts MySQL + app
make logs         # first start ~60s
make smoke        # end-to-end curl checks
make tools        # Adminer at http://localhost:8081 (server: mysql)
make db-reports   # sql/reports.sql
make orphans      # files without rows / rows without files
make down         # stop, keep data
make nuke         # stop and delete DB + uploads volumes
```

Raise limits through `.env`, using `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`, `..._MAX_REQUEST_SIZE` and `TELECOM_STORAGE_MAX_STREAM_SIZE`.

## 4. Kubernetes (minikube example)

```bash
minikube start --cpus=4 --memory=6g
minikube addons enable ingress
make k8s-deploy           # build, load image, kubectl apply -k k8s/
make k8s-status
make k8s-port-forward     # http://localhost:8080
make smoke                # in another terminal
make k8s-delete           # removes everything including PVCs
```

To use the ingress instead, map `<minikube ip> telecom-upload.local` in your hosts file. On the macOS or Windows Docker driver, run `minikube tunnel` and map it to `127.0.0.1`.

These manifests make some upload-specific choices:

| Manifest | Why |
|---|---|
| `15-uploads-pvc.yaml` | Uploaded files persist across pod restarts |
| `20-app-deployment.yaml` | 1 replica + `Recreate` (RWO volume), `fsGroup` so UID 10001 can write, `emptyDir` for multipart temp files, 60s grace period for in-flight uploads |
| `22-ingress.yaml` | `proxy-body-size: 250m` (nginx default is 1MB), request buffering off, 300s timeouts |

## 5. Try the API by hand

```bash
B=http://localhost:8080/api/v1/customers

curl -F "file=@sample-data/sample-id-proof.pdf" -F "type=ID_PROOF" -F "description=Govt ID" $B/1/documents
curl -F 'metadata={"type":"ADDRESS_PROOF"};type=application/json' \
     -F "files=@sample-data/sample-id-proof.pdf" $B/1/documents/batch          # 409 if same file already uploaded
curl -X POST --data-binary @sample-data/sample-id-proof.pdf -H "Content-Type: application/pdf" \
     -H "X-Filename: contract.pdf" "$B/2/documents/stream?type=CONTRACT"
curl $B/1/documents
curl -OJ $B/1/documents/1                                                       # saves with original name
curl -H "Range: bytes=0-99" $B/1/documents/1 -o first-100-bytes.bin
curl -X DELETE $B/1/documents/1
curl -F "file=@sample-data/fake-invoice.pdf" -F "type=OTHER" $B/1/documents     # 415
curl -F "file=@sample-data/customers-with-errors.csv" $B/import                 # row-level report
```

`requests.http` has the same requests for the IntelliJ HTTP Client or VS Code REST Client.

## Configuration reference

| Property / env var | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (jar) / `prod` (image) | profile |
| `spring.servlet.multipart.max-file-size` | 20MB | per file (multipart) |
| `spring.servlet.multipart.max-request-size` | 60MB | whole multipart request |
| `spring.servlet.multipart.file-size-threshold` | 512KB | memory vs temp file |
| `server.tomcat.max-swallow-size` | 60MB | lets Tomcat send a clean 413 |
| `TELECOM_STORAGE_LOCATION` | `./data/uploads` | storage root |
| `telecom.storage.allowed-content-types` | pdf, jpeg, png | detected types allowed |
| `TELECOM_STORAGE_MAX_STREAM_SIZE` | 200MB | stream endpoint limit |
| `telecom.storage.max-files-per-batch` | 10 | batch endpoint |
| `telecom.import.batch-size` | 500 | CSV rows per commit |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | – | prod database |

## Troubleshooting

| Symptom | Fix |
|---|---|
| Client sees "connection reset" instead of 413 | Raise `server.tomcat.max-swallow-size` above your largest rejected upload |
| 413 from nginx, not from the app (HTML body) | Raise the ingress or proxy `proxy-body-size` / `client_max_body_size` |
| "The temporary upload location is not valid" | The OS cleaned `/tmp`. Set `spring.servlet.multipart.location` to an existing directory |
| 415 for a real PDF | File doesn't start with `%PDF-` (e.g. it has a BOM or junk before it). Check with `head -c 8 file | xxd` |
| 409 on upload | Same bytes already on file for that customer (SHA-256 dedup) |
| `AccessDeniedException` writing uploads in Docker or K8s | The volume isn't writable by UID 10001. Keep `fsGroup: 10001` and the Dockerfile `chown` |
| K8s pod stuck `ContainerCreating` after an update | RWO volume still attached to the old pod. The `Recreate` strategy handles this, so wait or delete the old pod |
| `MySqlFlywayIT` skipped | Docker not running (expected). Start Docker to include it |
| Flyway checksum mismatch | An applied migration was edited. Add a new `V3__...`, or run `make nuke` locally |
