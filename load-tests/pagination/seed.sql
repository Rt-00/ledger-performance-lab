-- Gives one account a statement with 2,000,000 entries (1,000,000 transfers back and forth with
-- a second account) so deep pagination can be measured. Inserted directly with SQL because this
-- experiment is about reads; balances are reconciled at the end so the books still close.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO accounts (id, type, currency, balance_minor, status)
VALUES ('00000000-0000-7000-8000-00000000000a', 'CUSTOMER', 'BRL', 0, 'ACTIVE'),
       ('00000000-0000-7000-8000-00000000000b', 'CUSTOMER', 'BRL', 0, 'ACTIVE');

CREATE TEMP TABLE seed_transfers AS
SELECT gen_random_uuid() AS id, g AS n,
       CASE WHEN g % 2 = 0 THEN '00000000-0000-7000-8000-00000000000a'::uuid
            ELSE '00000000-0000-7000-8000-00000000000b'::uuid END AS source,
       CASE WHEN g % 2 = 0 THEN '00000000-0000-7000-8000-00000000000b'::uuid
            ELSE '00000000-0000-7000-8000-00000000000a'::uuid END AS target,
       timestamptz '2026-01-01' + g * interval '1 second' AS at
FROM generate_series(1, 1000000) AS g;

INSERT INTO transfers (id, idempotency_key, source_account_id, target_account_id, amount_minor, currency, occurred_at)
SELECT id, 'seed-' || n, source, target, 100, 'BRL', at FROM seed_transfers;

-- Debits and credits are balanced per account, so final balances are 0 and the books reconcile.
-- balance_after values are illustrative only: this data set exists to measure reads.
INSERT INTO ledger_entries (transfer_id, account_id, direction, amount_minor, balance_after_minor, currency, occurred_at)
SELECT id, source, 'DEBIT', 100, 0, 'BRL', at FROM seed_transfers
UNION ALL
SELECT id, target, 'CREDIT', 100, 100, 'BRL', at FROM seed_transfers
ORDER BY 1;

UPDATE accounts SET balance_minor = 0 WHERE id IN ('00000000-0000-7000-8000-00000000000a',
                                                   '00000000-0000-7000-8000-00000000000b');
COMMIT;
VACUUM ANALYZE ledger_entries;
