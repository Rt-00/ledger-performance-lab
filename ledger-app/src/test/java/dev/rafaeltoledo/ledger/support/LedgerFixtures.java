package dev.rafaeltoledo.ledger.support;

import dev.rafaeltoledo.ledger.application.Deposit;
import dev.rafaeltoledo.ledger.application.OpenAccount;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import java.util.Currency;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LedgerFixtures {

    public static final Currency BRL = Currency.getInstance("BRL");

    private final OpenAccount openAccount;
    private final Deposit deposit;

    public LedgerFixtures(OpenAccount openAccount, Deposit deposit) {
        this.openAccount = openAccount;
        this.deposit = deposit;
    }

    public static Money brl(String amount) {
        return Money.of(amount, BRL);
    }

    public static IdempotencyKey randomKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    public AccountId accountWith(String balance) {
        AccountId id = openAccount.execute(BRL).id();
        Money amount = brl(balance);
        if (amount.isPositive()) {
            deposit.execute(id, amount, randomKey());
        }
        return id;
    }
}
