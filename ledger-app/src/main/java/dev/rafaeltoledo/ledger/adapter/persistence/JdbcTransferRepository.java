package dev.rafaeltoledo.ledger.adapter.persistence;

import dev.rafaeltoledo.ledger.application.port.DuplicateIdempotencyKeyException;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.ExecutedTransfer;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.LedgerEntry;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferRepository implements TransferRepository {

    private static final String IDEMPOTENCY_CONSTRAINT = "transfers_idempotency_key_uk";

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JdbcTransferRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Transfer> findByIdempotencyKey(IdempotencyKey key) {
        return jdbc.sql("""
                        SELECT id, idempotency_key, source_account_id, target_account_id, amount_minor, currency, occurred_at
                        FROM transfers WHERE idempotency_key = ?""")
                .param(key.value())
                .query(JdbcTransferRepository::map)
                .optional();
    }

    @Override
    public void add(ExecutedTransfer executed) {
        Transfer t = executed.transfer();
        try {
            jdbc.sql("""
                            INSERT INTO transfers
                                (id, idempotency_key, source_account_id, target_account_id, amount_minor, currency, occurred_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)""")
                    .params(t.id().value(), t.idempotencyKey().value(), t.source().value(), t.target().value(),
                            t.amount().minorUnits(), t.amount().currency().getCurrencyCode(), utc(t))
                    .update();
        } catch (DuplicateKeyException e) {
            if (e.getMessage() != null && e.getMessage().contains(IDEMPOTENCY_CONSTRAINT)) {
                throw new DuplicateIdempotencyKeyException(t.idempotencyKey());
            }
            throw e;
        }
        insertEntries(executed.entries());
    }

    /** One round trip for all entries of the transfer (JDBC batch). */
    private void insertEntries(List<LedgerEntry> entries) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO ledger_entries
                            (transfer_id, account_id, direction, amount_minor, balance_after_minor, currency, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        LedgerEntry e = entries.get(i);
                        ps.setObject(1, e.transferId().value());
                        ps.setObject(2, e.accountId().value());
                        ps.setString(3, e.direction().name());
                        ps.setLong(4, e.amount().minorUnits());
                        ps.setLong(5, e.balanceAfter().minorUnits());
                        ps.setString(6, e.amount().currency().getCurrencyCode());
                        ps.setObject(7, e.occurredAt().atOffset(ZoneOffset.UTC));
                    }

                    @Override
                    public int getBatchSize() {
                        return entries.size();
                    }
                });
    }

    private static OffsetDateTime utc(Transfer t) {
        return t.occurredAt().atOffset(ZoneOffset.UTC);
    }

    private static Transfer map(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return new Transfer(
                new TransferId(rs.getObject("id", UUID.class)),
                new IdempotencyKey(rs.getString("idempotency_key")),
                AccountId.of(rs.getObject("source_account_id", UUID.class)),
                AccountId.of(rs.getObject("target_account_id", UUID.class)),
                Money.ofMinor(rs.getLong("amount_minor"), currency),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }
}
