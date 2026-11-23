package dev.rafaeltoledo.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.fakes.InMemoryLedger;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountStatus;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.domain.account.InsufficientFundsException;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransferFundsTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final List<Duration> sleeps = new ArrayList<>();
    private final RecordingObserver observer = new RecordingObserver();
    private AccountId alice;
    private AccountId bob;

    private static Money brl(String amount) {
        return Money.of(amount, BRL);
    }

    private TransferFunds useCase(LockMode lockMode) {
        return new TransferFunds(
                ledger, ledger, ledger, ledger,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new TransferSettings(lockMode, new RetryPolicy(3, Duration.ofMillis(10), sleeps::add)),
                observer);
    }

    private AccountId customerWith(String balance) {
        AccountId id = AccountId.newId();
        ledger.add(Account.restore(id, AccountType.CUSTOMER, brl(balance), AccountStatus.ACTIVE, 0));
        return id;
    }

    private TransferOrder order(String key, String amount) {
        return new TransferOrder(new IdempotencyKey(key), alice, bob, brl(amount));
    }

    @BeforeEach
    void setUp() {
        alice = customerWith("100.00");
        bob = customerWith("0.00");
    }

    @Test
    void moves_money_and_persists_transfer_entries_and_event_in_one_transaction() {
        TransferResult result = useCase(LockMode.PESSIMISTIC).execute(order("k1", "25.00"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().amount()).isEqualTo(brl("25.00"));
        assertThat(result.transfer().occurredAt()).isEqualTo(NOW);
        assertThat(ledger.account(alice).balance()).isEqualTo(brl("75.00"));
        assertThat(ledger.account(bob).balance()).isEqualTo(brl("25.00"));
        assertThat(ledger.account(alice).version()).isEqualTo(1);
        assertThat(ledger.entries()).hasSize(2);
        assertThat(ledger.events()).singleElement().satisfies(e -> assertThat(e.transferId()).isEqualTo(result.transfer().id()));
        assertThat(ledger.transactions).isEqualTo(1);
        assertThat(ledger.lockModesUsed).containsExactly(LockMode.PESSIMISTIC);
    }

    @Nested
    class Idempotency {

        @Test
        void replaying_the_same_order_returns_the_original_transfer_without_moving_money_again() {
            TransferFunds transferFunds = useCase(LockMode.PESSIMISTIC);
            TransferResult first = transferFunds.execute(order("k1", "25.00"));

            TransferResult replay = transferFunds.execute(order("k1", "25.00"));

            assertThat(replay.replayed()).isTrue();
            assertThat(replay.transfer()).isEqualTo(first.transfer());
            assertThat(ledger.account(alice).balance()).isEqualTo(brl("75.00"));
            assertThat(ledger.transfers()).hasSize(1);
            assertThat(observer.replays).isEqualTo(1);
        }

        @Test
        void reusing_a_key_with_a_different_payload_is_a_conflict() {
            TransferFunds transferFunds = useCase(LockMode.PESSIMISTIC);
            transferFunds.execute(order("k1", "25.00"));

            assertThatThrownBy(() -> transferFunds.execute(order("k1", "26.00")))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("k1");
        }

        @Test
        void a_concurrent_insert_of_the_same_key_is_resolved_as_a_replay() {
            Transfer winner = new Transfer(TransferId.newId(), new IdempotencyKey("k1"), alice, bob, brl("25.00"), NOW);
            ledger.transferInsertedConcurrently = winner;

            TransferResult result = useCase(LockMode.PESSIMISTIC).execute(order("k1", "25.00"));

            assertThat(result.replayed()).isTrue();
            assertThat(result.transfer()).isEqualTo(winner);
            assertThat(ledger.account(alice).balance())
                    .as("our attempt was rolled back").isEqualTo(brl("100.00"));
        }

        @Test
        void a_concurrent_insert_of_the_same_key_with_another_payload_is_a_conflict() {
            ledger.transferInsertedConcurrently =
                    new Transfer(TransferId.newId(), new IdempotencyKey("k1"), alice, bob, brl("1.00"), NOW);

            assertThatThrownBy(() -> useCase(LockMode.PESSIMISTIC).execute(order("k1", "25.00")))
                    .isInstanceOf(IdempotencyConflictException.class);
        }
    }

    @Nested
    class Failures {

        @Test
        void unknown_accounts_are_reported() {
            TransferOrder order = new TransferOrder(new IdempotencyKey("k"), alice, AccountId.newId(), brl("1.00"));

            assertThatThrownBy(() -> useCase(LockMode.PESSIMISTIC).execute(order))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining(order.target().toString());
        }

        @Test
        void business_rule_violations_roll_back_everything() {
            assertThatThrownBy(() -> useCase(LockMode.PESSIMISTIC).execute(order("k1", "100.01")))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(ledger.transfers()).isEmpty();
            assertThat(ledger.entries()).isEmpty();
            assertThat(ledger.events()).isEmpty();
            assertThat(observer.rejections).isEqualTo(1);
        }
    }

    @Nested
    class OptimisticLocking {

        @Test
        void retries_version_conflicts_with_exponential_backoff_and_then_succeeds() {
            ledger.pendingVersionConflicts = 2;

            TransferResult result = useCase(LockMode.OPTIMISTIC).execute(order("k1", "10.00"));

            assertThat(result.replayed()).isFalse();
            assertThat(ledger.account(alice).balance()).isEqualTo(brl("90.00"));
            assertThat(ledger.transactions).isEqualTo(3);
            assertThat(observer.retries).isEqualTo(2);
            assertThat(sleeps).hasSize(2);
            assertThat(sleeps.get(0)).isBetween(Duration.ofMillis(5), Duration.ofMillis(10));
            assertThat(sleeps.get(1)).isBetween(Duration.ofMillis(10), Duration.ofMillis(20));
        }

        @Test
        void gives_up_after_the_maximum_number_of_attempts() {
            ledger.pendingVersionConflicts = 10;

            assertThatThrownBy(() -> useCase(LockMode.OPTIMISTIC).execute(order("k1", "10.00")))
                    .isInstanceOf(TransferContentionException.class);

            assertThat(ledger.transactions).isEqualTo(3);
            assertThat(ledger.account(alice).balance()).isEqualTo(brl("100.00"));
        }

        @Test
        void pessimistic_mode_never_retries() {
            ledger.pendingVersionConflicts = 1;

            assertThatThrownBy(() -> useCase(LockMode.PESSIMISTIC).execute(order("k1", "10.00")))
                    .isInstanceOf(TransferContentionException.class);
            assertThat(ledger.transactions).isEqualTo(1);
        }
    }

    static final class RecordingObserver implements TransferObserver {
        int retries;
        int replays;
        int rejections;

        @Override
        public void retried(int attempt) {
            retries++;
        }

        @Override
        public void replayed() {
            replays++;
        }

        @Override
        public void rejected(RuntimeException reason) {
            rejections++;
        }
    }
}
