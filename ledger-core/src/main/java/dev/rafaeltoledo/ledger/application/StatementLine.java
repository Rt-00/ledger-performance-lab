package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.EntryDirection;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import java.time.Instant;

public record StatementLine(
        long entryId, TransferId transferId, EntryDirection direction, Money amount, Money balanceAfter, Instant occurredAt) {
}
