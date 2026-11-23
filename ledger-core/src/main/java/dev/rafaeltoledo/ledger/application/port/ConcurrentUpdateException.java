package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.domain.account.AccountId;

public final class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(AccountId accountId) {
        super("Account %s was modified by another transaction".formatted(accountId));
    }
}
