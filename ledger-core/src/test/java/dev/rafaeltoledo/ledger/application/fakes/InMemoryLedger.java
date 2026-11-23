package dev.rafaeltoledo.ledger.application.fakes;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.ConcurrentUpdateException;
import dev.rafaeltoledo.ledger.application.port.DuplicateIdempotencyKeyException;
import dev.rafaeltoledo.ledger.application.port.EventOutbox;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.AccountType;
import dev.rafaeltoledo.ledger.domain.transfer.ExecutedTransfer;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.LedgerEntry;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import dev.rafaeltoledo.ledger.domain.transfer.TransferCompleted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * In-memory implementation of the persistence ports with transactional semantics: state is
 * snapshotted when a transaction starts and restored if it throws, like a database rollback.
 * Accounts are copied on read so that only {@link #update} makes changes visible.
 */
public final class InMemoryLedger implements AccountRepository, TransferRepository, EventOutbox, TransactionRunner {

    private Map<AccountId, Account> accounts = new LinkedHashMap<>();
    private Map<IdempotencyKey, Transfer> transfers = new LinkedHashMap<>();
    private List<LedgerEntry> entries = new ArrayList<>();
    private List<TransferCompleted> events = new ArrayList<>();

    public int transactions;
    public int pendingVersionConflicts;
    public Transfer transferInsertedConcurrently;
    public final List<LockMode> lockModesUsed = new ArrayList<>();
    public final List<AccountId> updateOrder = new ArrayList<>();
    private Transfer committedByAnotherTransaction;

    // --- TransactionRunner ---

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        transactions++;
        var accountsSnapshot = new LinkedHashMap<>(accounts);
        var transfersSnapshot = new LinkedHashMap<>(transfers);
        var entriesSnapshot = new ArrayList<>(entries);
        var eventsSnapshot = new ArrayList<>(events);
        try {
            return work.get();
        } catch (RuntimeException e) {
            accounts = accountsSnapshot;
            transfers = transfersSnapshot;
            entries = entriesSnapshot;
            events = eventsSnapshot;
            if (committedByAnotherTransaction != null) {
                // The competing transaction committed independently of our rollback.
                transfers.put(committedByAnotherTransaction.idempotencyKey(), committedByAnotherTransaction);
                committedByAnotherTransaction = null;
            }
            throw e;
        }
    }

    // --- AccountRepository ---

    @Override
    public void add(Account account) {
        accounts.put(account.id(), copy(account, account.version()));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id)).map(a -> copy(a, a.version()));
    }

    @Override
    public Map<AccountId, Account> loadForUpdate(Collection<AccountId> ids, LockMode lockMode) {
        lockModesUsed.add(lockMode);
        Map<AccountId, Account> loaded = new HashMap<>();
        ids.stream().sorted().forEach(id -> findById(id).ifPresent(a -> loaded.put(id, a)));
        return loaded;
    }

    @Override
    public void update(Account account) {
        updateOrder.add(account.id());
        Account stored = accounts.get(account.id());
        if (pendingVersionConflicts > 0) {
            pendingVersionConflicts--;
            throw new ConcurrentUpdateException(account.id());
        }
        if (stored == null || stored.version() != account.version()) {
            throw new ConcurrentUpdateException(account.id());
        }
        accounts.put(account.id(), copy(account, account.version() + 1));
    }

    @Override
    public Optional<AccountId> findSettlementAccount(Currency currency) {
        return accounts.values().stream()
                .filter(a -> a.type() == AccountType.SETTLEMENT && a.currency().equals(currency))
                .map(Account::id)
                .findFirst();
    }

    // --- TransferRepository ---

    @Override
    public Optional<Transfer> findByIdempotencyKey(IdempotencyKey key) {
        return Optional.ofNullable(transfers.get(key));
    }

    @Override
    public void add(ExecutedTransfer executed) {
        if (transferInsertedConcurrently != null) {
            // Simulates another request committing the same key between our read and our insert.
            committedByAnotherTransaction = transferInsertedConcurrently;
            transferInsertedConcurrently = null;
            throw new DuplicateIdempotencyKeyException(executed.transfer().idempotencyKey());
        }
        if (transfers.containsKey(executed.transfer().idempotencyKey())) {
            throw new DuplicateIdempotencyKeyException(executed.transfer().idempotencyKey());
        }
        transfers.put(executed.transfer().idempotencyKey(), executed.transfer());
        entries.addAll(executed.entries());
    }

    // --- EventOutbox ---

    @Override
    public void append(TransferCompleted event) {
        events.add(event);
    }

    // --- inspection helpers for tests ---

    public Account account(AccountId id) {
        return accounts.get(id);
    }

    public Collection<Transfer> transfers() {
        return transfers.values();
    }

    public List<LedgerEntry> entries() {
        return entries;
    }

    public List<TransferCompleted> events() {
        return events;
    }

    private static Account copy(Account a, long version) {
        return Account.restore(a.id(), a.type(), a.balance(), a.status(), version);
    }
}
