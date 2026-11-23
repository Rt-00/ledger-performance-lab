package dev.rafaeltoledo.ledger.domain.account;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;

public final class AccountNotActiveException extends DomainException {

    public AccountNotActiveException(AccountId accountId, AccountStatus status) {
        super("account-not-active", "Account %s is %s".formatted(accountId, status));
    }
}
