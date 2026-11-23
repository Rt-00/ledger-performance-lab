package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;

public final class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(IdempotencyKey key) {
        super("idempotency-conflict",
                "Idempotency key '%s' was already used with a different payload".formatted(key.value()));
    }
}
