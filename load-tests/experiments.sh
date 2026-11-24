#!/usr/bin/env bash
# Runs every experiment from docs/experiments sequentially (~25 minutes).
# Each run restarts the stack with an empty database.
set -euo pipefail
cd "$(dirname "$0")/.."
run() { echo "== $1"; load-tests/run-experiment.sh "$@" | sed -n '/requests/,$p'; }

# E1: locking strategy x contention (64 concurrent clients, 200 accounts)
for round in 1 2; do
  for mode in pessimistic optimistic; do
    run "e1-${mode}-uniform-r${round}" LEDGER_LOCK_MODE=$mode HOT_RATIO=0 VUS=64
    run "e1-${mode}-hot50-r${round}"   LEDGER_LOCK_MODE=$mode HOT_RATIO=0.5 VUS=64
  done
done

# E3: connection pool size (uniform load, 64 clients)
for pool in 4 8 16 32 64; do
  run "e3-pool-${pool}" DB_POOL_SIZE=$pool HOT_RATIO=0 VUS=64
done

# E4: virtual vs platform threads with many concurrent clients
for vt in true false; do
  run "e4-virtual-${vt}" VIRTUAL_THREADS=$vt HOT_RATIO=0 VUS=400
done
