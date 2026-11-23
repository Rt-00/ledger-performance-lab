package dev.rafaeltoledo.ledger.adapter.persistence;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.application.StatementLine;
import dev.rafaeltoledo.ledger.application.StatementPage;
import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.application.port.StatementQuery;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.transfer.EntryDirection;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import dev.rafaeltoledo.ledger.support.LedgerFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class JdbcStatementQueryTest {

    @Autowired
    StatementQuery statements;

    @Autowired
    TransferFunds transferFunds;

    @Autowired
    LedgerFixtures fixtures;

    private AccountId alice;

    @BeforeEach
    void fiveTransfersOutOfAnAccountFundedWith100() {
        alice = fixtures.accountWith("100.00");
        AccountId bob = fixtures.accountWith("0.00");
        for (int i = 1; i <= 5; i++) {
            transferFunds.execute(new TransferOrder(LedgerFixtures.randomKey(), alice, bob, brl(i + ".00")));
        }
    }

    @Test
    void lists_newest_entries_first_with_running_balance() {
        StatementPage page = statements.after(alice, Optional.empty(), 10);

        assertThat(page.lines()).hasSize(6);
        StatementLine newest = page.lines().getFirst();
        assertThat(newest.direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(newest.amount()).isEqualTo(brl("5.00"));
        assertThat(newest.balanceAfter()).isEqualTo(brl("85.00"));
        assertThat(page.lines().getLast().direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(page.nextCursor()).isEmpty();
    }

    @Test
    void keyset_pages_walk_the_whole_statement_without_gaps_or_repeats() {
        List<StatementLine> walked = new ArrayList<>();
        Optional<Long> cursor = Optional.empty();
        do {
            StatementPage page = statements.after(alice, cursor, 4);
            walked.addAll(page.lines());
            cursor = page.nextCursor();
        } while (cursor.isPresent());

        assertThat(walked).hasSize(6).isEqualTo(statements.after(alice, Optional.empty(), 10).lines());
    }

    @Test
    void offset_pages_return_the_same_rows_as_keyset_pages() {
        StatementPage firstPage = statements.offset(alice, 0, 4);
        StatementPage secondPage = statements.offset(alice, 4, 4);

        assertThat(firstPage.lines()).isEqualTo(statements.after(alice, Optional.empty(), 4).lines());
        assertThat(firstPage.nextCursor()).isPresent();
        assertThat(secondPage.lines()).hasSize(2);
        assertThat(secondPage.nextCursor()).isEmpty();
    }
}
