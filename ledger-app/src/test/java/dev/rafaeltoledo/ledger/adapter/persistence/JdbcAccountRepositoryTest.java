package dev.rafaeltoledo.ledger.adapter.persistence;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.BRL;
import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.ConcurrentUpdateException;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountStatus;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Rolled back after each test: raw inserts here would otherwise break the global ledger audit. */
@IntegrationTest
@Transactional
class JdbcAccountRepositoryTest {

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcClient jdbc;

    private Account stored(String balance) {
        Account account = Account.restore(AccountId.newId(), AccountType.CUSTOMER, brl(balance), AccountStatus.ACTIVE, 0);
        accounts.add(account);
        return account;
    }

    @Test
    void round_trips_every_field() {
        Account account = stored("12.34");

        Account found = accounts.findById(account.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(account.id());
        assertThat(found.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(found.balance()).isEqualTo(brl("12.34"));
        assertThat(found.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(found.version()).isZero();
    }

    @Test
    void returns_empty_for_unknown_ids() {
        assertThat(accounts.findById(AccountId.newId())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(LockMode.class)
    void loads_existing_accounts_for_update_and_skips_missing_ones(LockMode mode) {
        Account a = stored("1.00");
        Account b = stored("2.00");
        AccountId missing = AccountId.newId();

        var loaded = accounts.loadForUpdate(List.of(b.id(), a.id(), missing), mode);

        assertThat(loaded).containsOnlyKeys(a.id(), b.id());
    }

    @Test
    void update_persists_changes_and_bumps_the_version() {
        Account account = stored("10.00");
        account.debit(brl("4.00"));
        account.block();

        accounts.update(account);

        Account found = accounts.findById(account.id()).orElseThrow();
        assertThat(found.balance()).isEqualTo(brl("6.00"));
        assertThat(found.status()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(found.version()).isEqualTo(1);
    }

    @Test
    void update_with_a_stale_version_is_rejected() {
        Account account = stored("10.00");
        Account staleCopy = accounts.findById(account.id()).orElseThrow();
        accounts.update(account);

        staleCopy.debit(brl("1.00"));

        assertThatThrownBy(() -> accounts.update(staleCopy)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(accounts.findById(account.id()).orElseThrow().balance()).isEqualTo(brl("10.00"));
    }

    @Test
    void the_seeded_settlement_accounts_are_found_by_currency() {
        assertThat(accounts.findSettlementAccount(BRL)).isPresent();
        assertThat(accounts.findSettlementAccount(Currency.getInstance("USD"))).isPresent();
        assertThat(accounts.findSettlementAccount(Currency.getInstance("EUR"))).isEmpty();
    }

    @Test
    void the_database_itself_refuses_a_negative_customer_balance() {
        Account account = stored("1.00");

        assertThatThrownBy(() -> jdbc.sql("UPDATE accounts SET balance_minor = -1 WHERE id = ?")
                        .param(account.id().value()).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_balance_never_negative");
    }
}
