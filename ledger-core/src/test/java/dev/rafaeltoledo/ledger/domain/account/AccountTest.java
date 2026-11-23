package dev.rafaeltoledo.ledger.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.domain.shared.CurrencyMismatchException;
import dev.rafaeltoledo.ledger.domain.shared.InvalidAmountException;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency BRL = Currency.getInstance("BRL");

    private static Money brl(String amount) {
        return Money.of(amount, BRL);
    }

    private static Account customerWith(String balance) {
        return Account.restore(AccountId.newId(), AccountType.CUSTOMER, brl(balance), AccountStatus.ACTIVE, 0);
    }

    @Test
    void a_new_account_starts_active_with_zero_balance() {
        Account account = Account.open(AccountId.newId(), AccountType.CUSTOMER, BRL);

        assertThat(account.balance()).isEqualTo(Money.zero(BRL));
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.currency()).isEqualTo(BRL);
        assertThat(account.version()).isZero();
    }

    @Nested
    class Debit {

        @Test
        void reduces_the_balance() {
            Account account = customerWith("100.00");

            account.debit(brl("30.00"));

            assertThat(account.balance()).isEqualTo(brl("70.00"));
        }

        @Test
        void can_take_the_balance_to_exactly_zero() {
            Account account = customerWith("10.00");

            account.debit(brl("10.00"));

            assertThat(account.balance()).isEqualTo(Money.zero(BRL));
        }

        @Test
        void never_lets_a_customer_account_go_negative() {
            Account account = customerWith("10.00");

            assertThatThrownBy(() -> account.debit(brl("10.01")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining(account.id().toString());
            assertThat(account.balance()).isEqualTo(brl("10.00"));
        }

        @Test
        void lets_a_settlement_account_go_negative() {
            Account settlement = Account.open(AccountId.newId(), AccountType.SETTLEMENT, BRL);

            settlement.debit(brl("500.00"));

            assertThat(settlement.balance()).isEqualTo(brl("-500.00"));
        }

        @Test
        void rejects_non_positive_amounts() {
            Account account = customerWith("10.00");

            assertThatThrownBy(() -> account.debit(Money.zero(BRL))).isInstanceOf(InvalidAmountException.class);
            assertThatThrownBy(() -> account.debit(brl("-1.00"))).isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void rejects_other_currencies() {
            Account account = customerWith("10.00");

            assertThatThrownBy(() -> account.debit(Money.of("1.00", Currency.getInstance("USD"))))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    class Credit {

        @Test
        void increases_the_balance() {
            Account account = customerWith("1.00");

            account.credit(brl("2.50"));

            assertThat(account.balance()).isEqualTo(brl("3.50"));
        }

        @Test
        void rejects_non_positive_amounts() {
            assertThatThrownBy(() -> customerWith("1.00").credit(Money.zero(BRL)))
                    .isInstanceOf(InvalidAmountException.class);
        }
    }

    @Nested
    class Blocking {

        @Test
        void a_blocked_account_cannot_move_money_in_either_direction() {
            Account account = customerWith("10.00");
            account.block();

            assertThatThrownBy(() -> account.debit(brl("1.00"))).isInstanceOf(AccountNotActiveException.class);
            assertThatThrownBy(() -> account.credit(brl("1.00"))).isInstanceOf(AccountNotActiveException.class);
            assertThat(account.status()).isEqualTo(AccountStatus.BLOCKED);
        }

        @Test
        void unblocking_restores_operations() {
            Account account = customerWith("10.00");
            account.block();
            account.unblock();

            account.debit(brl("1.00"));

            assertThat(account.balance()).isEqualTo(brl("9.00"));
        }
    }

    @Test
    void identity_is_defined_by_id_only() {
        AccountId id = AccountId.of(UUID.randomUUID());
        Account a = Account.restore(id, AccountType.CUSTOMER, brl("1.00"), AccountStatus.ACTIVE, 1);
        Account b = Account.restore(id, AccountType.CUSTOMER, brl("9.00"), AccountStatus.BLOCKED, 7);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(customerWith("1.00"));
    }

    @Test
    void account_id_rejects_null_and_round_trips_through_string() {
        AccountId id = AccountId.newId();

        assertThat(AccountId.of(id.toString())).isEqualTo(id);
        assertThatThrownBy(() -> AccountId.of((UUID) null)).isInstanceOf(NullPointerException.class);
    }
}
