# 01: Pessimistic vs optimistic locking

## Question
A transfer reads two balances and writes them back. Which strategy should protect them?

- **Pessimistic:** `SELECT ... ORDER BY id FOR NO KEY UPDATE`. The second transaction waits for
  the first one.
- **Optimistic:** read without locks, then `UPDATE ... WHERE version = ?`. On conflict the whole
  transaction is retried (up to 5 attempts, exponential backoff with jitter starting at 5 ms).
  After the last failed attempt the API answers `409 transfer-contention`.

## Setup
64 concurrent clients and 200 funded accounts, in two traffic shapes:
- **uniform**: random pairs of accounts (low contention);
- **hot 50%**: half of the transfers touch one specific account, like a marketplace or a
  settlement account.

Each configuration ran twice (`load-tests/experiments.sh`, runs `e1-*`).

## Results

| Lock mode | Traffic | Throughput (req/s) | p50 (ms) | p99 (ms) | Failed (409) | Retries |
|---|---|---|---|---|---|---|
| Pessimistic | uniform | 1,431 / 1,497 | 33.6 / 30.0 | 86.0 / 80.9 | 0 | 0 |
| Optimistic | uniform | 1,409 / 1,376 | 31.1 / 33.2 | 123.0 / 127.8 | 3 / 3 | ~9.8k |
| Pessimistic | hot 50% | 879 / 893 | 69.3 / 68.0 | 118.2 / 116.8 | 0 | 0 |
| Optimistic | hot 50% | 680 / 676 | 54.0 / 54.1 | 275.3 / 278.6 | **7,818 / 7,820** | ~55k |

(two rounds per row, round 1 / round 2. Retries come from the `ledger_transfers_total` counter
and include the warm-up.)

## What happened
- **Uniform traffic:** the two strategies are within noise on throughput. Optimistic still had
  about one retry for every eight transfers. 64 clients over 200 accounts collide more often than
  intuition suggests (the birthday problem), and each retry is a full transaction thrown away. You
  can see it in the p99: 125 ms against 83 ms.
- **Hot account:** pessimistic locking turns the hot row into a queue. Latency doubles, but every
  request succeeds. Optimistic locking turns it into a lottery: about 1.9 retries per successful
  transfer, a p99 2.4x higher, and **26% of requests still failing** after 5 attempts. The median
  looks better only because the requests that lost were never counted as successes.

## Decision
Pessimistic is the default (`ledger.lock-mode: pessimistic`). Optimistic stays available as
configuration because it suits read-mostly workloads with rare conflicts.

## The bug this experiment's test suite found
Before these runs, `LedgerConcurrencyTest` (16 threads x 100 random transfers against real
PostgreSQL) failed in optimistic mode with `ERROR: deadlock detected`. Without row locks up
front, transfer A->B updated A then B while a concurrent B->A updated B then A. The fix was to
write accounts in a global order (by id). A unit test now asserts the order, and the
concurrency test audits the books after every run: each balance equals the sum of its entries,
and each currency sums to zero.

## Next steps (not done)
A single hot account is a known limit of any row-per-balance design. The usual next steps are
splitting it into N sub-accounts (sharded counters) or batching postings for it asynchronously.
