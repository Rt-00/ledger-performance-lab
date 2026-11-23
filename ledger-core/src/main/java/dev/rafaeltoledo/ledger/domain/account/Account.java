package dev.rafaeltoledo.ledger.domain.account;

import dev.rafaeltoledo.ledger.domain.shared.CurrencyMismatchException;
import dev.rafaeltoledo.ledger.domain.shared.InvalidAmountException;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.util.Currency;
import java.util.Objects;

/**
 * Aggregate root that owns a balance and guards its invariants:
 * <ul>
 *   <li>money only moves in the account's currency and in positive amounts;</li>
 *   <li>a blocked account cannot be debited or credited;</li>
 *   <li>a customer account is never overdrawn.</li>
 * </ul>
 * The {@code version} is the value read from storage; persistence adapters use it for optimistic
 * concurrency control.
 */
public final class Account {

    private final AccountId id;
    private final AccountType type;
    private final long version;
    private Money balance;
    private AccountStatus status;

    private Account(AccountId id, AccountType type, Money balance, AccountStatus status, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
    }

    public static Account open(AccountId id, AccountType type, Currency currency) {
        return new Account(id, type, Money.zero(currency), AccountStatus.ACTIVE, 0);
    }

    public static Account restore(AccountId id, AccountType type, Money balance, AccountStatus status, long version) {
        return new Account(id, type, balance, status, version);
    }

    public void debit(Money amount) {
        requireMovable(amount);
        Money newBalance = balance.minus(amount);
        if (newBalance.isNegative() && !type.allowsNegativeBalance()) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance = newBalance;
    }

    public void credit(Money amount) {
        ensureCanBeCredited(amount);
        balance = balance.plus(amount);
    }

    /** Validates a future credit without mutating state, so multi-aggregate operations can fail fast. */
    public void ensureCanBeCredited(Money amount) {
        requireMovable(amount);
    }

    public void block() {
        status = AccountStatus.BLOCKED;
    }

    public void unblock() {
        status = AccountStatus.ACTIVE;
    }

    private void requireMovable(Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.currency().equals(currency())) {
            throw new CurrencyMismatchException(currency(), amount.currency());
        }
        if (!amount.isPositive()) {
            throw new InvalidAmountException("Amount must be positive, got " + amount);
        }
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id, status);
        }
    }

    public AccountId id() {
        return id;
    }

    public AccountType type() {
        return type;
    }

    public Currency currency() {
        return balance.currency();
    }

    public Money balance() {
        return balance;
    }

    public AccountStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Account other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Account[id=%s, type=%s, balance=%s, status=%s]".formatted(id, type, balance, status);
    }
}
