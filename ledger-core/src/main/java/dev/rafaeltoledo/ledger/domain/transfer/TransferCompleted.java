package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.time.Instant;

/** Domain event published (through the outbox) after a transfer is committed. */
public record TransferCompleted(TransferId transferId, AccountId source, AccountId target, Money amount, Instant occurredAt) {
}
