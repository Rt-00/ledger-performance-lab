package dev.rafaeltoledo.ledger.adapter.persistence;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.port.DuplicateIdempotencyKeyException;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.transfer.EntryDirection;
import dev.rafaeltoledo.ledger.domain.transfer.ExecutedTransfer;
import dev.rafaeltoledo.ledger.domain.transfer.LedgerEntry;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import dev.rafaeltoledo.ledger.support.LedgerFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Rolled back after each test: raw inserts here would otherwise break the global ledger audit. */
@IntegrationTest
@Transactional
class JdbcTransferRepositoryTest {

    @Autowired
    TransferRepository transfers;

    @Autowired
    LedgerFixtures fixtures;

    @Autowired
    JdbcClient jdbc;

    private ExecutedTransfer executed(AccountId from, AccountId to, String key) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Transfer transfer = new Transfer(TransferId.newId(), new dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey(key),
                from, to, brl("5.00"), now);
        return new ExecutedTransfer(transfer, List.of(
                new LedgerEntry(transfer.id(), from, EntryDirection.DEBIT, brl("5.00"), brl("5.00"), now),
                new LedgerEntry(transfer.id(), to, EntryDirection.CREDIT, brl("5.00"), brl("5.00"), now)));
    }

    @Test
    void stores_the_transfer_with_its_entries_and_finds_it_by_key() {
        AccountId a = fixtures.accountWith("10.00");
        AccountId b = fixtures.accountWith("0.00");
        String key = LedgerFixtures.randomKey().value();
        ExecutedTransfer executed = executed(a, b, key);

        transfers.add(executed);

        assertThat(transfers.findByIdempotencyKey(executed.transfer().idempotencyKey())).contains(executed.transfer());
        Integer entries = jdbc.sql("SELECT count(*) FROM ledger_entries WHERE transfer_id = ?")
                .param(executed.transfer().id().value()).query(Integer.class).single();
        assertThat(entries).isEqualTo(2);
    }

    @Test
    void a_second_transfer_with_the_same_key_is_reported_as_duplicate() {
        AccountId a = fixtures.accountWith("10.00");
        AccountId b = fixtures.accountWith("0.00");
        String key = LedgerFixtures.randomKey().value();
        transfers.add(executed(a, b, key));

        assertThatThrownBy(() -> transfers.add(executed(a, b, key)))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    void other_integrity_violations_are_not_mistaken_for_duplicates() {
        AccountId a = fixtures.accountWith("10.00");

        assertThatThrownBy(() -> transfers.add(executed(a, AccountId.newId(), "fk-violation")))
                .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
    }
}
