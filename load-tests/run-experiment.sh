#!/usr/bin/env bash
# Usage: load-tests/run-experiment.sh <name> [KEY=VALUE ...]
#   App settings : LEDGER_LOCK_MODE, VIRTUAL_THREADS, DB_POOL_SIZE, FLYWAY_TARGET
#   k6 settings  : SCRIPT (default transfers.js), VUS, DURATION, WARMUP, HOT_RATIO, ACCOUNTS
# Restarts the app with a clean database so every run starts from the same state.
set -euo pipefail
cd "$(dirname "$0")/.."

name="$1"; shift
for kv in "$@"; do export "${kv?}"; done
script="${SCRIPT:-transfers.js}"

docker compose --profile app down -v --remove-orphans >/dev/null 2>&1 || true
docker compose --profile app up -d --build --wait >/dev/null
until curl -fs http://localhost:8080/actuator/health >/dev/null; do sleep 1; done

docker run --rm --network host --user "$(id -u):$(id -g)" \
  -v "$PWD/load-tests:/scripts:ro" -v "$PWD/load-tests/results:/results" \
  -e NAME="$name" -e VUS -e DURATION -e WARMUP -e HOT_RATIO -e ACCOUNTS -e BASE_URL \
  grafana/k6:latest run --quiet "/scripts/$script"

curl -fs http://localhost:8080/actuator/prometheus | grep -E '^ledger_transfers_total' \
  > "load-tests/results/$name.metrics.txt" || true
