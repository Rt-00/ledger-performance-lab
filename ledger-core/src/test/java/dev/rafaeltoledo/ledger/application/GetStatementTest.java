package dev.rafaeltoledo.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.fakes.InMemoryLedger;
import dev.rafaeltoledo.ledger.application.port.StatementQuery;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GetStatementTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final RecordingQuery query = new RecordingQuery();
    private final GetStatement getStatement = new GetStatement(ledger, query);

    private AccountId existingAccount() {
        AccountId id = AccountId.newId();
        ledger.add(Account.open(id, AccountType.CUSTOMER, Currency.getInstance("BRL")));
        return id;
    }

    @Test
    void reads_a_page_after_the_given_cursor() {
        AccountId id = existingAccount();

        StatementPage page = getStatement.byCursor(id, Optional.of(42L), 20);

        assertThat(page).isSameAs(RecordingQuery.PAGE);
        assertThat(query.lastCall).isEqualTo("cursor " + id + " 42 20");
    }

    @Test
    void reads_a_page_by_offset() {
        AccountId id = existingAccount();

        getStatement.byOffset(id, 3, 50);

        assertThat(query.lastCall).isEqualTo("offset " + id + " 150 50");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201})
    void rejects_page_sizes_outside_1_to_200(int size) {
        AccountId id = existingAccount();

        assertThatThrownBy(() -> getStatement.byCursor(id, Optional.empty(), size))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> getStatement.byOffset(id, 0, size))
                .isInstanceOf(InvalidPageRequestException.class);
    }

    @Test
    void rejects_negative_pages() {
        AccountId id = existingAccount();

        assertThatThrownBy(() -> getStatement.byOffset(id, -1, 10)).isInstanceOf(InvalidPageRequestException.class);
    }

    @Test
    void reports_unknown_accounts() {
        assertThatThrownBy(() -> getStatement.byCursor(AccountId.newId(), Optional.empty(), 10))
                .isInstanceOf(AccountNotFoundException.class);
    }

    static final class RecordingQuery implements StatementQuery {
        static final StatementPage PAGE = new StatementPage(List.of(), Optional.empty());
        String lastCall;

        @Override
        public StatementPage after(AccountId accountId, Optional<Long> cursor, int limit) {
            lastCall = "cursor " + accountId + " " + cursor.map(String::valueOf).orElse("-") + " " + limit;
            return PAGE;
        }

        @Override
        public StatementPage offset(AccountId accountId, long offset, int limit) {
            lastCall = "offset " + accountId + " " + offset + " " + limit;
            return PAGE;
        }
    }
}
