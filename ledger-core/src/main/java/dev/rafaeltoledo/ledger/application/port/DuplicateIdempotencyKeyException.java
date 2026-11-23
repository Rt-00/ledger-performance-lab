package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;

public final class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(IdempotencyKey key) {
        super("A transfer with idempotency key '%s' already exists".formatted(key.value()));
    }
}
