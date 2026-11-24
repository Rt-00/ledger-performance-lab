-- Makes (account_id, id) the primary key of ledger_entries and drops the separate statement
-- index (docs/experiments/02-pagination.md).
--
-- Statements filter by account and walk ids backwards. With a primary key on id alone, the
-- planner could serve `ORDER BY id DESC LIMIT n` by scanning the primary key backwards and
-- filtering by account. It assumes account_id and id are independent, but ids grow with time, so
-- an account's oldest entries sit among everybody else's. The last page of a 1M-entry statement
-- read ~110k buffers (20 ms) instead of ~10. With no index on id alone, that plan no longer
-- exists, and the table keeps one index instead of two (cheaper inserts, 83 MB instead of 194 MB).
--
-- Production-safe sequence: build the unique index without blocking writes, then swap the
-- constraint (a short lock). CONCURRENTLY cannot run inside a transaction, hence the .conf file.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ledger_entries_account_id_id_uk ON ledger_entries (account_id, id);

ALTER TABLE ledger_entries
    DROP CONSTRAINT ledger_entries_pkey,
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY USING INDEX ledger_entries_account_id_id_uk;

DROP INDEX CONCURRENTLY IF EXISTS ledger_entries_account_statement_idx;
