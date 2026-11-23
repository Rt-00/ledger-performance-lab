package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.transfer.Transfer;

/** {@code replayed} is true when the idempotency key had already been processed. */
public record TransferResult(Transfer transfer, boolean replayed) {
}
