package dev.rafaeltoledo.ledger.adapter.persistence;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.ConcurrentUpdateException;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountStatus;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final String SELECT = "SELECT id, type, currency, balance_minor, status, version FROM accounts ";

    /*
     * ORDER BY id makes every transaction lock rows in the same order, which rules out deadlocks
     * between A->B and B->A transfers. FOR NO KEY UPDATE (instead of FOR UPDATE) does not conflict
     * with the FOR KEY SHARE locks taken by foreign key checks when other transactions insert
     * transfers/entries referencing these accounts.
     */
    private static final String LOCK_PESSIMISTIC = SELECT + "WHERE id = ANY(?) ORDER BY id FOR NO KEY UPDATE";
    private static final String LOCK_OPTIMISTIC = SELECT + "WHERE id = ANY(?)";

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Account account) {
        jdbc.sql("""
                        INSERT INTO accounts (id, type, currency, balance_minor, status, version)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .params(account.id().value(), account.type().name(), account.currency().getCurrencyCode(),
                        account.balance().minorUnits(), account.status().name(), account.version())
                .update();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.sql(SELECT + "WHERE id = ?").param(id.value()).query(JdbcAccountRepository::map).optional();
    }

    @Override
    public Map<AccountId, Account> loadForUpdate(Collection<AccountId> ids, LockMode lockMode) {
        UUID[] uuids = ids.stream().map(AccountId::value).distinct().toArray(UUID[]::new);
        Map<AccountId, Account> loaded = new LinkedHashMap<>();
        jdbc.sql(lockMode == LockMode.PESSIMISTIC ? LOCK_PESSIMISTIC : LOCK_OPTIMISTIC)
                .param(uuids)
                .query(JdbcAccountRepository::map)
                .list()
                .forEach(account -> loaded.put(account.id(), account));
        return loaded;
    }

    @Override
    public void update(Account account) {
        int updated = jdbc.sql("""
                        UPDATE accounts
                        SET balance_minor = ?, status = ?, version = version + 1
                        WHERE id = ? AND version = ?""")
                .params(account.balance().minorUnits(), account.status().name(), account.id().value(), account.version())
                .update();
        if (updated == 0) {
            throw new ConcurrentUpdateException(account.id());
        }
    }

    @Override
    public Optional<AccountId> findSettlementAccount(Currency currency) {
        return jdbc.sql("SELECT id FROM accounts WHERE type = 'SETTLEMENT' AND currency = ?")
                .param(currency.getCurrencyCode())
                .query((rs, n) -> AccountId.of(rs.getObject("id", UUID.class)))
                .optional();
    }

    private static Account map(ResultSet rs, int rowNum) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency"));
        return Account.restore(
                AccountId.of(rs.getObject("id", UUID.class)),
                AccountType.valueOf(rs.getString("type")),
                Money.ofMinor(rs.getLong("balance_minor"), currency),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }
}
