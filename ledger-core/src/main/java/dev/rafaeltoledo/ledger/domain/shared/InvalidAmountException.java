package dev.rafaeltoledo.ledger.domain.shared;

public final class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super("invalid-amount", message);
    }
}
