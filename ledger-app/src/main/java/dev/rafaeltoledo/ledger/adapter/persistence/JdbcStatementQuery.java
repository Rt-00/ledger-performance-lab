package dev.rafaeltoledo.ledger.adapter.persistence;

import dev.rafaeltoledo.ledger.application.StatementLine;
import dev.rafaeltoledo.ledger.application.StatementPage;
import dev.rafaeltoledo.ledger.application.port.StatementQuery;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.EntryDirection;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Both queries fetch {@code limit + 1} rows: the extra row only tells whether another page exists,
 * which avoids a separate COUNT(*) (itself an O(n) scan on large statements).
 */
@Repository
class JdbcStatementQuery implements StatementQuery {

    private static final String COLUMNS =
            "SELECT id, transfer_id, direction, amount_minor, balance_after_minor, currency, occurred_at FROM ledger_entries ";

    private final JdbcClient jdbc;

    JdbcStatementQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StatementPage after(AccountId accountId, Optional<Long> cursor, int limit) {
        List<StatementLine> rows = jdbc.sql(COLUMNS + "WHERE account_id = ? AND id < ? ORDER BY id DESC LIMIT ?")
                .params(accountId.value(), cursor.orElse(Long.MAX_VALUE), limit + 1)
                .query(JdbcStatementQuery::map)
                .list();
        return page(rows, limit);
    }

    @Override
    public StatementPage offset(AccountId accountId, long offset, int limit) {
        List<StatementLine> rows = jdbc.sql(COLUMNS + "WHERE account_id = ? ORDER BY id DESC OFFSET ? LIMIT ?")
                .params(accountId.value(), offset, limit + 1)
                .query(JdbcStatementQuery::map)
                .list();
        return page(rows, limit);
    }

    private static StatementPage page(List<StatementLine> rows, int limit) {
        if (rows.size() <= limit) {
            return new StatementPage(rows, Optional.empty());
        }
        List<StatementLine> lines = rows.subList(0, limit);
        return new StatementPage(lines, Optional.of(lines.getLast().entryId()));
    }

    private static StatementLine map(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new StatementLine(
                rs.getLong("id"),
                new TransferId(rs.getObject("transfer_id", UUID.class)),
                EntryDirection.valueOf(rs.getString("direction")),
                Money.ofMinor(rs.getLong("amount_minor"), currency),
                Money.ofMinor(rs.getLong("balance_after_minor"), currency),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }
}
