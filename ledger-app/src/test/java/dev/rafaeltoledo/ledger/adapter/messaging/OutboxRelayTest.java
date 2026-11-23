package dev.rafaeltoledo.ledger.adapter.messaging;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import dev.rafaeltoledo.ledger.support.LedgerFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxRelayTest {

    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired TransferFunds transferFunds;
    @Autowired LedgerFixtures fixtures;
    @Autowired MeterRegistry meters;

    @BeforeEach
    void drainEventsLeftByOtherTests() {
        new OutboxRelay(jdbc, tx, messages -> { }, 10_000).publishPending();
    }

    private TransferId transfer() {
        AccountId a = fixtures.accountWith("10.00");
        AccountId b = fixtures.accountWith("0.00");
        return transferFunds.execute(new TransferOrder(LedgerFixtures.randomKey(), a, b, brl("1.00"))).transfer().id();
    }

    private long unpublished() {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL").query(Long.class).single();
    }

    @Test
    void publishes_pending_events_in_order_and_marks_them_as_published() {
        TransferId first = transfer();
        TransferId second = transfer();
        List<OutboxMessage> published = new ArrayList<>();
        OutboxRelay relay = new OutboxRelay(jdbc, tx, published::addAll, 100);

        int count = relay.publishPending();

        // fixtures deposit before each transfer, so there are deposit events in between
        assertThat(count).isEqualTo(published.size()).isGreaterThanOrEqualTo(2);
        assertThat(published).extracting(OutboxMessage::aggregateId).contains(first.value(), second.value());
        assertThat(published).extracting(OutboxMessage::id).isSorted();
        assertThat(published).allSatisfy(m -> {
            assertThat(m.type()).isEqualTo("TransferCompleted");
            assertThat(m.payload()).contains("\"transferId\"");
        });
        assertThat(unpublished()).isZero();
        assertThat(relay.publishPending()).isZero();
    }

    @Test
    void respects_the_batch_size() {
        transfer();
        transfer();
        OutboxRelay relay = new OutboxRelay(jdbc, tx, messages -> { }, 1);

        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(unpublished()).isGreaterThan(0);
    }

    @Test
    void a_failed_publish_leaves_the_events_pending_for_the_next_run() {
        transfer();
        long pending = unpublished();
        OutboxRelay failing = new OutboxRelay(jdbc, tx, messages -> {
            throw new IllegalStateException("broker down");
        }, 100);

        assertThatThrownBy(failing::publishPending).hasMessage("broker down");

        assertThat(unpublished()).isEqualTo(pending);
    }

    @Test
    void concurrent_relays_never_publish_the_same_event_twice() throws Exception {
        for (int i = 0; i < 20; i++) {
            transfer();
        }
        List<Long> published = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int t = 0; t < 4; t++) {
                pool.submit(() -> {
                    start.await();
                    OutboxRelay relay = new OutboxRelay(jdbc, tx, batch -> {
                        batch.forEach(m -> published.add(m.id()));
                        sleep();
                    }, 5);
                    while (relay.publishPending() > 0) {
                        // keep draining
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(published).doesNotHaveDuplicates().hasSizeGreaterThanOrEqualTo(20);
        assertThat(unpublished()).isZero();
    }

    @Test
    void exposes_the_backlog_as_a_gauge() {
        transfer();

        assertThat(meters.get("ledger.outbox.pending").gauge().value()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unknown_payloads_are_still_relayed_verbatim() {
        jdbc.sql("INSERT INTO outbox_events (aggregate_id, event_type, payload) VALUES (?, 'Other', '{\"x\":1}'::jsonb)")
                .param(UUID.randomUUID()).update();
        List<OutboxMessage> published = new ArrayList<>();

        new OutboxRelay(jdbc, tx, published::addAll, 100).publishPending();

        assertThat(published).singleElement().satisfies(m -> {
            assertThat(m.type()).isEqualTo("Other");
            assertThat(m.payload()).isEqualTo("{\"x\": 1}");
        });
    }

    private static void sleep() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
