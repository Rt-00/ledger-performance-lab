package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;

public final class GetAccount {

    private final AccountRepository accounts;

    public GetAccount(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account execute(AccountId id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
