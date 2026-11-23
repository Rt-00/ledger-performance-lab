package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;

/** Raised when a transfer kept losing optimistic-lock races; the client may retry later. */
public final class TransferContentionException extends DomainException {

    public TransferContentionException(int attempts, Throwable cause) {
        super("transfer-contention", "Transfer could not be applied after %d attempt(s) due to concurrent updates"
                .formatted(attempts));
        initCause(cause);
    }
}
