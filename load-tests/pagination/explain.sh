#!/usr/bin/env bash
# Compares OFFSET and keyset pagination at increasing depths on a 1M-entry statement.
# Prints execution time and shared buffers touched for each query (EXPLAIN ANALYZE, warm cache).
set -euo pipefail
cd "$(dirname "$0")/../.."
psql() { docker compose exec -T postgres psql -U ledger -d ledger -qAt "$@"; }
ACCOUNT="00000000-0000-7000-8000-00000000000a"
PAGE=50
# Same columns as JdbcStatementQuery.
COLUMNS="id, transfer_id, direction, amount_minor, balance_after_minor, currency, occurred_at"

echo "depth,offset_ms,offset_buffers,keyset_ms,keyset_buffers"
for depth in 0 1000 10000 100000 500000 999950; do
  cursor=$(psql -c "SELECT coalesce(min(id), 9223372036854775807) FROM (SELECT id FROM ledger_entries
                    WHERE account_id = '$ACCOUNT' ORDER BY id DESC LIMIT $depth) t" | tr -d '[:space:]')
  [ "$depth" = 0 ] && cursor=9223372036854775807
  measure() {
    for _ in 1 2 3; do psql -c "$1" >/dev/null; done   # warm up
    psql -c "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) $1" | python3 -c '
import json,sys; p=json.load(sys.stdin)[0]
print(round(p["Execution Time"],3), p["Plan"]["Shared Hit Blocks"]+p["Plan"].get("Shared Read Blocks",0), sep=",", end="")'
  }
  offset=$(measure "SELECT $COLUMNS FROM ledger_entries WHERE account_id = '$ACCOUNT' ORDER BY id DESC OFFSET $depth LIMIT $((PAGE + 1))")
  keyset=$(measure "SELECT $COLUMNS FROM ledger_entries WHERE account_id = '$ACCOUNT' AND id < $cursor ORDER BY id DESC LIMIT $((PAGE + 1))")
  echo "$depth,$offset,$keyset"
done
