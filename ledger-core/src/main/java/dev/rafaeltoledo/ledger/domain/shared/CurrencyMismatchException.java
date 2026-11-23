package dev.rafaeltoledo.ledger.domain.shared;

import java.util.Currency;

public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("currency-mismatch", "Expected currency %s but got %s".formatted(expected, actual));
    }
}
