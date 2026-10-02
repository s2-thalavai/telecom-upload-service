#!/usr/bin/env bash
# Lists stored files that have no customer_documents row (and rows whose file is missing).
# Usage (Compose): ./scripts/find-orphans.sh
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] && set -a && source .env && set +a
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

docker compose exec -T mysql mysql -N -u"${MYSQL_USER:-telecom_user}" -p"${MYSQL_PASSWORD:-telecom_pass_change_me}" \
  "${MYSQL_DATABASE:-telecomdb}" -e "SELECT storage_key FROM customer_documents" 2>/dev/null | sort > "$TMP/db.txt"
docker compose exec -T app sh -c 'ls -1 /app/data/uploads' | grep -v '\.part$' | sort > "$TMP/disk.txt" || true

echo "Files on disk without a DB row:";  comm -13 "$TMP/db.txt" "$TMP/disk.txt" | sed 's/^/  /'
echo "DB rows whose file is missing:";   comm -23 "$TMP/db.txt" "$TMP/disk.txt" | sed 's/^/  /'
