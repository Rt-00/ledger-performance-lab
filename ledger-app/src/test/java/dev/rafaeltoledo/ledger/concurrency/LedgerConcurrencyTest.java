package dev.rafaeltoledo.ledger.concurrency;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.application.RetryPolicy;
import dev.rafaeltoledo.ledger.application.TransferContentionException;
import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.application.TransferObserver;
import dev.rafaeltoledo.ledger.application.TransferResult;
import dev.rafaeltoledo.ledger.application.TransferSettings;
import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.EventOutbox;
import dev.rafaeltoledo.ledger.application.port.LockMode;
import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.InsufficientFundsException;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import dev.rafaeltoledo.ledger.support.LedgerFixtures;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Hammers the real database with concurrent transfers and then audits the books. Whatever the
 * interleaving, double-entry accounting must hold.
 */
@IntegrationTest
class LedgerConcurrencyTest {

    private static final int ACCOUNTS = 20;
    private static final int THREADS = 16;
    private static final int TRANSFERS_PER_THREAD = 100;

    @Autowired AccountRepository accounts;
    @Autowired TransferRepository transfers;
    @Autowired EventOutbox outbox;
    @Autowired TransactionRunner tx;
    @Autowired Clock clock;
    @Autowired LedgerFixtures fixtures;
    @Autowired JdbcClient jdbc;

    private TransferFunds transferFunds(LockMode mode) {
        return new TransferFunds(accounts, transfers, outbox, tx, clock,
                new TransferSettings(mode, new RetryPolicy(50, Duration.ofMillis(1), RetryPolicy.Sleeper.THREAD_SLEEP)),
                TransferObserver.NO_OP);
    }

    @ParameterizedTest
    @EnumSource(LockMode.class)
    void random_concurrent_transfers_keep_the_books_balanced(LockMode mode) throws Exception {
        List<AccountId> ids = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            ids.add(fixtures.accountWith("1000.00"));
        }
        TransferFunds transferFunds = transferFunds(mode);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger insufficientFunds = new AtomicInteger();
        AtomicInteger contention = new AtomicInteger();

        List<Throwable> unexpected = runConcurrently(THREADS, () -> () -> {
            for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                var random = ThreadLocalRandom.current();
                AccountId from = ids.get(random.nextInt(ACCOUNTS));
                AccountId to = ids.get(random.nextInt(ACCOUNTS));
                if (from.equals(to)) {
                    continue;
                }
                try {
                    transferFunds.execute(new TransferOrder(LedgerFixtures.randomKey(), from, to,
                            brl(random.nextInt(1, 300) + ".00")));
                    completed.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficientFunds.incrementAndGet();
                } catch (TransferContentionException e) {
                    contention.incrementAndGet();
                }
            }
        });

        assertThat(unexpected).as("deadlocks or other unexpected failures").isEmpty();
        assertThat(completed.get()).isGreaterThan(THREADS * TRANSFERS_PER_THREAD / 2);
        if (mode == LockMode.PESSIMISTIC) {
            assertThat(contention.get()).isZero();
        }

        long totalOfTheseAccounts = ids.stream()
                .mapToLong(id -> accounts.findById(id).orElseThrow().balance().minorUnits()).sum();
        assertThat(totalOfTheseAccounts).as("money is neither created nor destroyed").isEqualTo(ACCOUNTS * 100_000L);
        assertThat(ids).allSatisfy(id ->
                assertThat(accounts.findById(id).orElseThrow().balance().isNegative()).isFalse());
        assertBooksReconcile();
    }

    @Test
    void opposite_transfers_on_the_same_pair_never_deadlock() throws Exception {
        AccountId a = fixtures.accountWith("10000.00");
        AccountId b = fixtures.accountWith("10000.00");
        TransferFunds transferFunds = transferFunds(LockMode.PESSIMISTIC);
        AtomicInteger direction = new AtomicInteger();

        List<Throwable> unexpected = runConcurrently(THREADS, () -> {
            boolean aToB = direction.getAndIncrement() % 2 == 0;
            return () -> {
                for (int i = 0; i < 50; i++) {
                    transferFunds.execute(new TransferOrder(LedgerFixtures.randomKey(),
                            aToB ? a : b, aToB ? b : a, brl("1.00")));
                }
            };
        });

        assertThat(unexpected).isEmpty();
        assertThat(accounts.findById(a).orElseThrow().balance()).isEqualTo(brl("10000.00"));
        assertBooksReconcile();
    }

    @Test
    void the_same_idempotency_key_submitted_concurrently_moves_money_once() throws Exception {
        AccountId a = fixtures.accountWith("100.00");
        AccountId b = fixtures.accountWith("0.00");
        TransferOrder order = new TransferOrder(new IdempotencyKey("race-" + UUID.randomUUID()), a, b, brl("10.00"));
        TransferFunds transferFunds = transferFunds(LockMode.PESSIMISTIC);
        List<TransferResult> results = Collections.synchronizedList(new ArrayList<>());

        List<Throwable> unexpected = runConcurrently(THREADS, () -> () -> results.add(transferFunds.execute(order)));

        assertThat(unexpected).isEmpty();
        assertThat(results).hasSize(THREADS);
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(results).extracting(r -> r.transfer().id()).containsOnly(results.getFirst().transfer().id());
        assertThat(accounts.findById(b).orElseThrow().balance()).isEqualTo(brl("10.00"));
    }

    /** Every balance equals the sum of its entries, and all balances of a currency sum to zero. */
    private void assertBooksReconcile() {
        List<Map<String, Object>> drift = jdbc.sql("""
                SELECT a.id, a.balance_minor, coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount_minor
                                                                           ELSE -e.amount_minor END), 0) AS from_entries
                FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.balance_minor
                HAVING a.balance_minor <> coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount_minor
                                                                        ELSE -e.amount_minor END), 0)
                """).query().listOfRows();
        assertThat(drift).as("accounts whose balance does not match their entries").isEmpty();

        List<Long> totalsPerCurrency = jdbc.sql("SELECT sum(balance_minor) FROM accounts GROUP BY currency")
                .query(Long.class).list();
        assertThat(totalsPerCurrency).as("double-entry: each currency sums to zero").containsOnly(0L);
    }

    private static List<Throwable> runConcurrently(int threads, Supplier<ThrowingRunnable> workPerThread) throws Exception {
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                ThrowingRunnable work = workPerThread.get();
                executor.submit(() -> {
                    try {
                        start.await();
                        work.run();
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                    return null;
                });
            }
            start.countDown();
        }
        return new ArrayList<>(failures);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
