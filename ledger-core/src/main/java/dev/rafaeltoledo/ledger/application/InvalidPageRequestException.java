package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;

public final class InvalidPageRequestException extends DomainException {

    public InvalidPageRequestException(String message) {
        super("invalid-page-request", message);
    }
}
