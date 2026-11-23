package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.util.Objects;

/** The intention to move {@code amount} from {@code source} to {@code target}. */
public record TransferOrder(IdempotencyKey idempotencyKey, AccountId source, AccountId target, Money amount) {

    public TransferOrder {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
    }
}
