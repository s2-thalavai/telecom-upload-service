#!/usr/bin/env bash
# Delete everything including PVCs (database rows AND uploaded files are lost).
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl delete -k k8s/ --ignore-not-found
kubectl -n telecom-upload delete pvc --all --ignore-not-found 2>/dev/null || true
kubectl delete namespace telecom-upload --ignore-not-found
