# 02: Statement pagination (OFFSET vs keyset) and a planner trap

## Question
An account statement is read newest first, in pages of 50. How does the cost evolve with depth
for `OFFSET n LIMIT 50` and for keyset pagination (`WHERE id < :cursor LIMIT 50`)?

## Setup
One account with **1,000,000 entries** (`load-tests/pagination/seed.sql`), inside a table of
about 2.1M entries. Each query is run 3 times to warm the cache, then measured with
`EXPLAIN (ANALYZE, BUFFERS)` (`load-tests/pagination/explain.sh`). "Buffers" = 8 kB pages
touched, a better proxy for cost than milliseconds on a laptop.

## Round 1: schema V1, `PRIMARY KEY (id)` + index `(account_id, id DESC)`

| Depth (rows skipped) | OFFSET ms | OFFSET buffers | Keyset ms | Keyset buffers |
|---|---|---|---|---|
| 0 | 0.13 | 6 | 0.07 | 6 |
| 1,000 | 0.56 | 38 | 0.15 | 9 |
| 10,000 | 3.37 | 327 | 0.10 | 9 |
| 100,000 | 21.08 | 3,219 | 0.17 | 9 |
| 500,000 | 88.06 | 16,072 | 0.16 | 9 |
| 999,950 (last page) | 330.30 | 142,082 | **20.88** | **109,955** |

OFFSET grows linearly: the database still reads and discards every skipped row. Keyset stays
flat at about 9 buffers, **except on the last page**, where it suddenly costs 12,000x more.

### The trap
```
Limit (actual rows=50)
  ->  Index Scan Backward using ledger_entries_pkey
        Index Cond: (id < 136527)
        Filter: (account_id = '...000a')
        Rows Removed by Filter: 136476
```
To serve `ORDER BY id DESC LIMIT 51`, PostgreSQL may walk the **primary key on `id`** backwards
and filter by account, stopping once it has 51 rows. It estimated 64,499 matching rows below the
cursor, assuming `account_id` and `id` are independent. They are not: ids grow with time, and
below this cursor there were only 50 rows of this account among 136k rows of other accounts. The
`LIMIT` never filled up early, so the scan read everything down to the start of the table. Any
account whose history is old compared to the rest of the table can hit this.

## Round 2: covering index (rejected)
Adding `INCLUDE (...)` to the statement index made index-only scans possible. On a database
where the index was built **after** loading the data (compact, 213 MB), the planner chose it and
the last page dropped to 0.16 ms. On a database **populated through inserts** the index was
392 MB, the planner went back to the primary key, and the last page took 20.3 ms and 118k
buffers again. A fix that depends on index bloat and planner cost estimates is not a fix.

## Round 3: `PRIMARY KEY (account_id, id)` (adopted, migration V2)
Nothing in the system looks up an entry by `id` alone: statements always filter by account. So
the primary key became `(account_id, id)` and the separate statement index was dropped. **There
is no longer an index on `id` alone, so the bad plan cannot be chosen anymore**, whatever the
estimates say.

| Depth | OFFSET ms | OFFSET buffers | Keyset ms | Keyset buffers |
|---|---|---|---|---|
| 0 | 0.15 | 5 | 0.08 | 5 |
| 1,000 | 0.49 | 40 | 0.10 | 8 |
| 10,000 | 2.26 | 360 | 0.18 | 9 |
| 100,000 | 18.16 | 3,556 | 0.10 | 8 |
| 500,000 | 112.29 | 17,763 | 0.13 | 9 |
| 999,950 (last page) | 251.68 | 35,523 | **0.14** | **11** |

(fresh database, seeded through inserts, `e2-pagination-composite-pk.csv`)

### Cost on writes
Indexes on `ledger_entries`: one (155 MB) instead of two (pk 46 MB + statement 148 MB on the
round 1 dataset). Write throughput, measured **interleaved in the same session** with 64
clients (`e5-*`):

| Schema | Round 1 | Round 2 |
|---|---|---|
| V1: pk(id) + (account_id, id) | 1,191 req/s | 1,329 req/s |
| V2: pk(account_id, id) | 1,346 req/s | 1,336 req/s |

No difference beyond noise. A lesson from this step: the first comparison of these runs used
the baseline from experiment 01, measured before a reboot, and suggested a 10% loss. Running the
two variants alternately in the same session showed that gap was the machine, not the schema.

## Decisions
- Statements use **keyset pagination**: constant cost at any depth, and pages stay stable while
  new entries arrive (OFFSET shifts rows between pages under concurrent inserts).
  `?page=` is kept only to reproduce this experiment.
- `PRIMARY KEY (account_id, id)`, built with `CREATE UNIQUE INDEX CONCURRENTLY` and then
  `ADD PRIMARY KEY USING INDEX`. This needed `spring.flyway.postgresql.transactional-lock: false`:
  Flyway's lock keeps a transaction open, and `CONCURRENTLY` waits for it forever (the test suite
  hung until this was found with `pg_stat_activity`).
- `StatementIndexTest` pins both the schema and the plan shape, so a future index change cannot
  silently bring the trap back.
