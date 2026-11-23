package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import java.util.Currency;

public final class OpenAccount {

    private final AccountRepository accounts;
    private final TransactionRunner tx;

    public OpenAccount(AccountRepository accounts, TransactionRunner tx) {
        this.accounts = accounts;
        this.tx = tx;
    }

    public Account execute(Currency currency) {
        Account account = Account.open(AccountId.newId(), AccountType.CUSTOMER, currency);
        return tx.inTransaction(() -> {
            accounts.add(account);
            return account;
        });
    }
}
