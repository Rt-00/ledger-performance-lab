# ADR 0002: JDBC (JdbcClient) instead of JPA on the hot path

**Status:** accepted

## Context
A transfer touches two account rows, inserts one transfer and two entries and appends an event.
Correctness under concurrency depends on *exactly* which locks are taken and in which order.

## Decision
Use Spring's `JdbcClient`/`JdbcTemplate` with hand-written SQL:

- `SELECT ... WHERE id = ANY(?) ORDER BY id FOR NO KEY UPDATE` for pessimistic locking: one
  round trip, deterministic lock order, and `NO KEY` so it does not block foreign-key checks
  (`FOR KEY SHARE`) from concurrent inserts referencing the same accounts.
- `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?` for optimistic locking.
- JDBC batch for ledger entries.

## Consequences
- Every statement and lock is visible in code review and in `EXPLAIN`.
- No dirty checking, no lazy loading, no N+1 surprises; mapping code is written by hand.
- With JPA the same design is possible (`@Lock(PESSIMISTIC_WRITE)`, `@Version`), but the lock
  order and the `NO KEY` variant are harder to control.
