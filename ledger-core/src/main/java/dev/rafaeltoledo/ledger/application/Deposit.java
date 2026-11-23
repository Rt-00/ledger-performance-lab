package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;

/**
 * Brings external money into the ledger as a transfer from the currency's settlement account.
 * Keeping deposits double-entry means the sum of all balances per currency is always zero, which
 * is the invariant the concurrency tests check.
 */
public final class Deposit {

    private final AccountRepository accounts;
    private final TransferFunds transferFunds;

    public Deposit(AccountRepository accounts, TransferFunds transferFunds) {
        this.accounts = accounts;
        this.transferFunds = transferFunds;
    }

    public TransferResult execute(AccountId accountId, Money amount, IdempotencyKey key) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        AccountId settlement = accounts.findSettlementAccount(account.currency())
                .orElseThrow(() -> new SettlementAccountMissingException(account.currency()));
        return transferFunds.execute(new TransferOrder(key, settlement, accountId, amount));
    }
}
