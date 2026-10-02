#!/usr/bin/env bash
# End-to-end upload checks against a running instance.
# Usage: BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
set -uo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1/customers"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
PASS=0; FAIL=0

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
ok()    { green "PASS  $1"; PASS=$((PASS + 1)); }
ko()    { red   "FAIL  $1"; FAIL=$((FAIL + 1)); }
expect_status() { # name expected actual
  if [[ "$3" == "$2" ]]; then ok "$1 ($3)"; else ko "$1 (got $3, want $2)"; fi
}
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

echo "Waiting for $BASE_URL/actuator/health ..."
for _ in $(seq 1 60); do curl -fs "$BASE_URL/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
curl -fs "$BASE_URL/actuator/health" >/dev/null || { red "Service not healthy at $BASE_URL"; exit 1; }

# Unique customer for this run
N=$(( (RANDOM * 32768 + RANDOM) % 100000000 ))
MSISDN=$(printf '9%09d' "$N")
CUSTOMER_JSON=$(curl -s -X POST "$API" -H "Content-Type: application/json" \
  -d "{\"name\":\"Smoke $N\",\"email\":\"smoke$N@example.com\",\"msisdn\":\"$MSISDN\",\"planType\":\"PREPAID\"}")
CID=$(echo "$CUSTOMER_JSON" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
[[ -n "$CID" ]] && ok "create customer id=$CID" || { ko "create customer: $CUSTOMER_JSON"; exit 1; }
DOCS="$API/$CID/documents"

# Unique PDF (so dedup does not trigger across runs)
{ cat sample-data/sample-id-proof.pdf; echo "% smoke $N $(date +%s)"; } > "$TMP/id.pdf"

echo; echo "== Multipart upload =="
BODY=$(curl -s -w '\n%{http_code}' -F "file=@$TMP/id.pdf" -F "type=ID_PROOF" -F "description=Smoke test" "$DOCS")
STATUS=$(echo "$BODY" | tail -1); JSON=$(echo "$BODY" | sed '$d')
expect_status "upload PDF" 201 "$STATUS"
DOC_URL=$(echo "$JSON" | sed -n 's/.*"downloadUrl":"\([^"]*\)".*/\1/p')

curl -s -o "$TMP/downloaded.pdf" "$BASE_URL$DOC_URL"
[[ "$(sha "$TMP/id.pdf")" == "$(sha "$TMP/downloaded.pdf")" ]] && ok "download matches upload (sha256)" || ko "download checksum mismatch"

expect_status "duplicate upload -> 409" 409 \
  "$(curl -s -o /dev/null -w '%{http_code}' -F "file=@$TMP/id.pdf" -F "type=ID_PROOF" "$DOCS")"
expect_status "disguised executable -> 415" 415 \
  "$(curl -s -o /dev/null -w '%{http_code}' -F "file=@sample-data/fake-invoice.pdf" -F "type=OTHER" "$DOCS")"
expect_status "missing file part -> 400" 400 \
  "$(curl -s -o /dev/null -w '%{http_code}' -F "type=OTHER" "$DOCS")"

echo; echo "== Batch upload (JSON metadata part) =="
{ cat sample-data/sample-id-proof.pdf; echo "% jan $N"; } > "$TMP/jan.pdf"
{ cat sample-data/sample-id-proof.pdf; echo "% feb $N"; } > "$TMP/feb.pdf"
expect_status "batch upload" 201 "$(curl -s -o /dev/null -w '%{http_code}' \
  -F 'metadata={"type":"ADDRESS_PROOF","description":"bills"};type=application/json' \
  -F "files=@$TMP/jan.pdf" -F "files=@$TMP/feb.pdf" "$DOCS/batch")"

echo; echo "== Streaming upload =="
{ cat sample-data/sample-id-proof.pdf; head -c 3000000 /dev/zero | tr '\0' 'x'; } > "$TMP/big.pdf"
expect_status "stream 3MB PDF" 201 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary "@$TMP/big.pdf" \
  -H "Content-Type: application/pdf" -H "X-Filename: big.pdf" "$DOCS/stream?type=CONTRACT")"

echo; echo "== List / delete =="
expect_status "list documents" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$DOCS")"
expect_status "delete document" 204 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL$DOC_URL")"
expect_status "download deleted -> 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$DOC_URL")"

echo; echo "== CSV import =="
printf 'name,email,msisdn,planType\nImport %s,import%s@example.com,%s,PREPAID\nBad Email,not-an-email,%s,PREPAID\n' \
  "$N" "$N" "$(printf '7%09d' "$N")" "$(printf '6%09d' "$N")" > "$TMP/import.csv"
IMPORT=$(curl -s -F "file=@$TMP/import.csv" "$API/import")
echo "$IMPORT" | tr -d ' \n' | grep -q '"imported":1,"failed":1' \
  && ok "CSV import: 1 imported, 1 rejected" || ko "CSV import: $IMPORT"

echo; echo "Passed: $PASS  Failed: $FAIL"
[[ $FAIL -eq 0 ]]
