# ADR 0001: Framework-free core with ports and adapters

**Status:** accepted

## Context
The interesting part of a ledger is its rules (no overdraft, double entry, idempotency) and how
they hold under concurrency. Those rules should be testable in milliseconds, without a database
or a Spring context, and should not change when infrastructure changes.

## Decision
- `ledger-core` (Gradle module) contains `domain` and `application` packages and has **no
  framework on its classpath**: plain Java 21.
- The application layer talks to the outside world through ports
  (`AccountRepository`, `TransferRepository`, `EventOutbox`, `TransactionRunner`, `StatementQuery`).
- `ledger-app` holds Spring Boot, the adapters (web, persistence, messaging, metrics) and the
  only place where use cases become beans (`UseCaseConfiguration`).
- ArchUnit enforces it: the domain depends only on `java..`, the application only on the domain,
  adapters do not depend on each other.

## Consequences
- Use cases are unit-tested with in-memory fakes that even simulate rollback, so races such as
  "duplicate idempotency key inserted concurrently" are tested without a database.
- The transaction boundary is explicit (`TransactionRunner`) instead of `@Transactional` on
  core classes; retries happen *around* the transaction, which is what optimistic locking needs.
- A little more wiring code than an all-Spring service.
