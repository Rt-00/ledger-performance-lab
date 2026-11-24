# ADR 0004: Transactional outbox for `TransferCompleted` events

**Status:** accepted

## Context
Publishing to RabbitMQ inside the database transaction can publish an event for a transfer that
is later rolled back; publishing after commit can lose the event if the process dies in between.

## Decision
- The use case appends the event to `outbox_events` in the **same transaction** as the transfer.
- `OutboxRelay` polls with `SELECT ... FOR UPDATE SKIP LOCKED LIMIT n`, publishes the batch with
  publisher confirms and sets `published_at` in the same transaction that holds the row locks.
- `SKIP LOCKED` lets several relay instances work in parallel on disjoint batches.

## Consequences
- Delivery is **at-least-once**: a crash after the broker ack and before commit re-publishes.
  Messages carry `messageId = <type>:<aggregateId>` so consumers can deduplicate.
- Events are delayed by up to the polling interval (200 ms by default).
- A partial index (`WHERE published_at IS NULL`) keeps the relay query cheap as the table grows.
