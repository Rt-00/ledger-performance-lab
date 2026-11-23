package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;

/** An executed, immutable transfer. */
public record Transfer(
        TransferId id,
        IdempotencyKey idempotencyKey,
        AccountId source,
        AccountId target,
        Money amount,
        Instant occurredAt) {

    public Transfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Whether a retried order carries the same payload as the one that produced this transfer. */
    public boolean isSameOrderAs(TransferOrder order) {
        return idempotencyKey.equals(order.idempotencyKey())
                && source.equals(order.source())
                && target.equals(order.target())
                && amount.equals(order.amount());
    }
}
