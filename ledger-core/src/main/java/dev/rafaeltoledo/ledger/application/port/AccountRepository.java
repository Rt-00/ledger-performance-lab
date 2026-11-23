package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

public interface AccountRepository {

    void add(Account account);

    Optional<Account> findById(AccountId id);

    /** Loads the given accounts for modification. Missing ids are simply absent from the result. */
    Map<AccountId, Account> loadForUpdate(Collection<AccountId> ids, LockMode lockMode);

    /**
     * Persists the account if its stored version still equals {@link Account#version()}.
     *
     * @throws ConcurrentUpdateException if another transaction changed the account in the meantime
     */
    void update(Account account);

    Optional<AccountId> findSettlementAccount(Currency currency);
}
