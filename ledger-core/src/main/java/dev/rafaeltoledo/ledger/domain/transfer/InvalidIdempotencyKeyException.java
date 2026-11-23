package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;

public final class InvalidIdempotencyKeyException extends DomainException {

    public InvalidIdempotencyKeyException(String message) {
        super("invalid-idempotency-key", message);
    }
}
