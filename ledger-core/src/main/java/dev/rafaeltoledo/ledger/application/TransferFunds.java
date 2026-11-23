package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.ConcurrentUpdateException;
import dev.rafaeltoledo.ledger.application.port.DuplicateIdempotencyKeyException;
import dev.rafaeltoledo.ledger.application.port.EventOutbox;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import dev.rafaeltoledo.ledger.domain.transfer.FundsTransfer;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Executes a transfer exactly once per idempotency key.
 *
 * <p>Inside one transaction it: returns the stored result if the key was already used, loads both
 * accounts with the configured {@link LockMode}, applies the domain rules, stores the transfer and
 * its entries, saves both accounts and appends the event to the outbox.
 *
 * <p>Two races are handled outside the transaction: a version conflict (optimistic mode) retries
 * the whole unit of work with backoff, and a duplicate key (two requests with the same key at the
 * same time) rolls back and answers with the transfer that won.
 */
public final class TransferFunds {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final EventOutbox outbox;
    private final TransactionRunner tx;
    private final Clock clock;
    private final TransferSettings settings;
    private final TransferObserver observer;
    private final FundsTransfer fundsTransfer = new FundsTransfer();

    public TransferFunds(
            AccountRepository accounts,
            TransferRepository transfers,
            EventOutbox outbox,
            TransactionRunner tx,
            Clock clock,
            TransferSettings settings,
            TransferObserver observer) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
        this.settings = settings;
        this.observer = observer;
    }

    public TransferResult execute(TransferOrder order) {
        RetryPolicy retry = settings.retryPolicy();
        int maxAttempts = settings.lockMode() == LockMode.OPTIMISTIC ? retry.maxAttempts() : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.inTransaction(() -> attempt(order));
            } catch (ConcurrentUpdateException e) {
                if (attempt >= maxAttempts) {
                    throw new TransferContentionException(attempt, e);
                }
                observer.retried(attempt);
                retry.sleeper().sleep(retry.backoff(attempt));
            } catch (DuplicateIdempotencyKeyException e) {
                return tx.inTransaction(() -> transfers.findByIdempotencyKey(order.idempotencyKey())
                        .map(winner -> replay(winner, order))
                        .orElseThrow(() -> e));
            } catch (DomainException e) {
                observer.rejected(e);
                throw e;
            }
        }
    }

    private TransferResult attempt(TransferOrder order) {
        var previous = transfers.findByIdempotencyKey(order.idempotencyKey());
        if (previous.isPresent()) {
            return replay(previous.get(), order);
        }

        Map<AccountId, Account> loaded =
                accounts.loadForUpdate(List.of(order.source(), order.target()), settings.lockMode());
        Account source = require(loaded, order.source());
        Account target = require(loaded, order.target());

        var executed = fundsTransfer.execute(order, source, target, clock.instant());

        transfers.add(executed);
        accounts.update(source);
        accounts.update(target);
        outbox.append(executed.completedEvent());
        observer.completed();
        return new TransferResult(executed.transfer(), false);
    }

    private TransferResult replay(Transfer previous, TransferOrder order) {
        if (!previous.isSameOrderAs(order)) {
            throw new IdempotencyConflictException(order.idempotencyKey());
        }
        observer.replayed();
        return new TransferResult(previous, true);
    }

    private static Account require(Map<AccountId, Account> loaded, AccountId id) {
        Account account = loaded.get(id);
        if (account == null) {
            throw new AccountNotFoundException(id);
        }
        return account;
    }
}
