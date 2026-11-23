package dev.rafaeltoledo.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.fakes.InMemoryLedger;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountStatus;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountUseCasesTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency USD = Currency.getInstance("USD");

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final TransferFunds transferFunds = new TransferFunds(
            ledger, ledger, ledger, ledger,
            Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC),
            new TransferSettings(LockMode.PESSIMISTIC, new RetryPolicy(1, Duration.ofMillis(1), d -> { })),
            TransferObserver.NO_OP);

    @Nested
    class Opening {

        @Test
        void opens_an_active_customer_account_with_zero_balance() {
            Account account = new OpenAccount(ledger, ledger).execute(BRL);

            Account stored = ledger.account(account.id());
            assertThat(stored.type()).isEqualTo(AccountType.CUSTOMER);
            assertThat(stored.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(stored.balance()).isEqualTo(Money.zero(BRL));
        }
    }

    @Nested
    class Querying {

        @Test
        void finds_an_existing_account() {
            Account opened = new OpenAccount(ledger, ledger).execute(BRL);

            assertThat(new GetAccount(ledger).execute(opened.id())).isEqualTo(opened);
        }

        @Test
        void reports_unknown_accounts() {
            AccountId unknown = AccountId.newId();

            assertThatThrownBy(() -> new GetAccount(ledger).execute(unknown))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(unknown.toString());
        }
    }

    @Nested
    class Depositing {

        @Test
        void moves_money_from_the_settlement_account_so_the_ledger_still_sums_to_zero() {
            AccountId settlement = AccountId.newId();
            ledger.add(Account.open(settlement, AccountType.SETTLEMENT, BRL));
            Account customer = new OpenAccount(ledger, ledger).execute(BRL);

            TransferResult result = new Deposit(ledger, transferFunds)
                    .execute(customer.id(), Money.of("50.00", BRL), new IdempotencyKey("dep-1"));

            assertThat(result.transfer().source()).isEqualTo(settlement);
            assertThat(ledger.account(customer.id()).balance()).isEqualTo(Money.of("50.00", BRL));
            assertThat(ledger.account(settlement).balance()).isEqualTo(Money.of("-50.00", BRL));
        }

        @Test
        void fails_when_no_settlement_account_exists_for_the_currency() {
            Account customer = new OpenAccount(ledger, ledger).execute(USD);

            assertThatThrownBy(() -> new Deposit(ledger, transferFunds)
                            .execute(customer.id(), Money.of("1.00", USD), new IdempotencyKey("dep-1")))
                    .isInstanceOf(SettlementAccountMissingException.class)
                    .hasMessageContaining("USD");
        }
    }
}
