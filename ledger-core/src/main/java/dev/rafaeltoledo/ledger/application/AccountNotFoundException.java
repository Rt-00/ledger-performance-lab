package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.DomainException;

public final class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(AccountId id) {
        super("account-not-found", "Account %s does not exist".formatted(id));
    }
}
