-- Accounts hold the current balance; ledger_entries are the immutable history that explains it.

CREATE TABLE accounts (
    id            UUID        PRIMARY KEY,
    type          VARCHAR(16) NOT NULL CHECK (type IN ('CUSTOMER', 'SETTLEMENT')),
    currency      CHAR(3)     NOT NULL,
    balance_minor BIGINT      NOT NULL,
    status        VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Defense in depth: the domain already forbids overdrafts, the database enforces it again.
    CONSTRAINT customer_balance_never_negative CHECK (type = 'SETTLEMENT' OR balance_minor >= 0)
);

CREATE UNIQUE INDEX one_settlement_account_per_currency ON accounts (currency) WHERE type = 'SETTLEMENT';

CREATE TABLE transfers (
    id                UUID         PRIMARY KEY,
    idempotency_key   VARCHAR(100) NOT NULL,
    source_account_id UUID         NOT NULL REFERENCES accounts (id),
    target_account_id UUID         NOT NULL REFERENCES accounts (id),
    amount_minor      BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency          CHAR(3)      NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT transfers_idempotency_key_uk UNIQUE (idempotency_key),
    CONSTRAINT transfers_distinct_accounts CHECK (source_account_id <> target_account_id)
);

CREATE TABLE ledger_entries (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id         UUID        NOT NULL REFERENCES transfers (id),
    account_id          UUID        NOT NULL REFERENCES accounts (id),
    direction           VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor        BIGINT      NOT NULL CHECK (amount_minor > 0),
    balance_after_minor BIGINT      NOT NULL,
    currency            CHAR(3)     NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL
);

-- Serves statements (newest first) with an index-only range scan; see docs/experiments.
CREATE INDEX ledger_entries_account_statement_idx ON ledger_entries (account_id, id DESC);

CREATE TABLE outbox_events (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- Partial index: the relay only ever looks at unpublished events, so the index stays tiny.
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (id) WHERE published_at IS NULL;

-- Contra accounts used by deposits (fixed ids so they are easy to find in dashboards and tests).
INSERT INTO accounts (id, type, currency, balance_minor, status)
VALUES ('00000000-0000-7000-8000-000000000986', 'SETTLEMENT', 'BRL', 0, 'ACTIVE'),
       ('00000000-0000-7000-8000-000000000840', 'SETTLEMENT', 'USD', 0, 'ACTIVE');
