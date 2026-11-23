package dev.rafaeltoledo.ledger.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountNotActiveException;
import dev.rafaeltoledo.ledger.domain.account.AccountStatus;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.domain.account.InsufficientFundsException;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class FundsTransferTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private final FundsTransfer fundsTransfer = new FundsTransfer();

    private static Money brl(String amount) {
        return Money.of(amount, BRL);
    }

    private static Account customer(String balance) {
        return Account.restore(AccountId.newId(), AccountType.CUSTOMER, brl(balance), AccountStatus.ACTIVE, 0);
    }

    private static TransferOrder order(Account from, Account to, String amount) {
        return new TransferOrder(new IdempotencyKey("order-1"), from.id(), to.id(), brl(amount));
    }

    @Test
    void moves_money_and_records_one_debit_and_one_credit_entry() {
        Account alice = customer("100.00");
        Account bob = customer("5.00");

        ExecutedTransfer executed = fundsTransfer.execute(order(alice, bob, "40.00"), alice, bob, NOW);

        assertThat(alice.balance()).isEqualTo(brl("60.00"));
        assertThat(bob.balance()).isEqualTo(brl("45.00"));
        assertThat(executed.entries())
                .extracting(LedgerEntry::accountId, LedgerEntry::direction, LedgerEntry::amount, LedgerEntry::balanceAfter)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(alice.id(), EntryDirection.DEBIT, brl("40.00"), brl("60.00")),
                        org.assertj.core.groups.Tuple.tuple(bob.id(), EntryDirection.CREDIT, brl("40.00"), brl("45.00")));
        assertThat(executed.entries()).allSatisfy(e -> {
            assertThat(e.transferId()).isEqualTo(executed.transfer().id());
            assertThat(e.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    void entries_always_balance_to_zero() {
        Account alice = customer("100.00");
        Account bob = customer("0.00");

        ExecutedTransfer executed = fundsTransfer.execute(order(alice, bob, "12.34"), alice, bob, NOW);

        long signedSum = executed.entries().stream().mapToLong(LedgerEntry::signedMinorUnits).sum();
        assertThat(signedSum).isZero();
    }

    @Test
    void the_transfer_keeps_the_order_data() {
        Account alice = customer("10.00");
        Account bob = customer("0.00");
        TransferOrder order = order(alice, bob, "1.00");

        Transfer transfer = fundsTransfer.execute(order, alice, bob, NOW).transfer();

        assertThat(transfer.idempotencyKey()).isEqualTo(order.idempotencyKey());
        assertThat(transfer.source()).isEqualTo(alice.id());
        assertThat(transfer.target()).isEqualTo(bob.id());
        assertThat(transfer.amount()).isEqualTo(brl("1.00"));
        assertThat(transfer.occurredAt()).isEqualTo(NOW);
        assertThat(transfer.isSameOrderAs(order)).isTrue();
        assertThat(transfer.isSameOrderAs(order(alice, bob, "2.00"))).isFalse();
    }

    @Test
    void is_atomic_in_memory_when_the_debit_fails_nothing_changes() {
        Account alice = customer("10.00");
        Account bob = customer("0.00");

        assertThatThrownBy(() -> fundsTransfer.execute(order(alice, bob, "10.01"), alice, bob, NOW))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(alice.balance()).isEqualTo(brl("10.00"));
        assertThat(bob.balance()).isEqualTo(brl("0.00"));
    }

    @Test
    void is_atomic_in_memory_when_the_credit_would_fail_the_source_is_not_debited() {
        Account alice = customer("10.00");
        Account bob = customer("0.00");
        bob.block();

        assertThatThrownBy(() -> fundsTransfer.execute(order(alice, bob, "1.00"), alice, bob, NOW))
                .isInstanceOf(AccountNotActiveException.class);

        assertThat(alice.balance()).isEqualTo(brl("10.00"));
    }

    @Test
    void rejects_transfers_to_the_same_account() {
        Account alice = customer("10.00");
        TransferOrder order = new TransferOrder(new IdempotencyKey("k"), alice.id(), alice.id(), brl("1.00"));

        assertThatThrownBy(() -> fundsTransfer.execute(order, alice, alice, NOW))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void rejects_accounts_that_do_not_match_the_order() {
        Account alice = customer("10.00");
        Account bob = customer("0.00");
        Account carol = customer("0.00");

        assertThatThrownBy(() -> fundsTransfer.execute(order(alice, bob, "1.00"), alice, carol, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fundsTransfer.execute(order(alice, bob, "1.00"), carol, bob, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void produces_a_completed_event() {
        Account alice = customer("10.00");
        Account bob = customer("0.00");

        ExecutedTransfer executed = fundsTransfer.execute(order(alice, bob, "3.00"), alice, bob, NOW);
        TransferCompleted event = executed.completedEvent();

        assertThat(event.transferId()).isEqualTo(executed.transfer().id());
        assertThat(event.source()).isEqualTo(alice.id());
        assertThat(event.target()).isEqualTo(bob.id());
        assertThat(event.amount()).isEqualTo(brl("3.00"));
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }
}
