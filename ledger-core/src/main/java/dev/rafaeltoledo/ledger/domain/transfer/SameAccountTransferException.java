package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.DomainException;

public final class SameAccountTransferException extends DomainException {

    public SameAccountTransferException(AccountId accountId) {
        super("same-account-transfer", "Cannot transfer from account %s to itself".formatted(accountId));
    }
}
