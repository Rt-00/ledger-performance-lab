package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.time.Instant;

/** One side of a double-entry posting. {@code balanceAfter} makes statements cheap to read. */
public record LedgerEntry(
        TransferId transferId,
        AccountId accountId,
        EntryDirection direction,
        Money amount,
        Money balanceAfter,
        Instant occurredAt) {

    public long signedMinorUnits() {
        return direction == EntryDirection.DEBIT ? -amount.minorUnits() : amount.minorUnits();
    }
}
