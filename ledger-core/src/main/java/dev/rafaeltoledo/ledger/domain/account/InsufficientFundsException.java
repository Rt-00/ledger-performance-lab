package dev.rafaeltoledo.ledger.domain.account;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import dev.rafaeltoledo.ledger.domain.shared.Money;

public final class InsufficientFundsException extends DomainException {

    public InsufficientFundsException(AccountId accountId, Money balance, Money requested) {
        super("insufficient-funds",
                "Account %s has %s available but %s was requested".formatted(accountId, balance, requested));
    }
}
