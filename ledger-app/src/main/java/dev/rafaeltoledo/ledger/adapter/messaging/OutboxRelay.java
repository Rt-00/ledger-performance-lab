package dev.rafaeltoledo.ledger.adapter.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves committed events from {@code outbox_events} to the broker.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} lets any number of relay instances run in parallel: each one
 * claims a different batch instead of waiting on, or re-publishing, rows another instance holds.
 * Rows are marked as published only after the broker confirmed the batch, inside the same
 * transaction that locked them; a crash in between leads to a re-publish (at-least-once), never
 * to a lost event. Consumers deduplicate by message id.
 */
public class OutboxRelay {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final EventPublisher publisher;
    private final int batchSize;

    OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, EventPublisher publisher, int batchSize) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    /** @return how many events were published in this run */
    @Scheduled(fixedDelayString = "${ledger.outbox.relay.fixed-delay:200ms}")
    public int publishPending() {
        Integer published = tx.execute(status -> {
            List<OutboxMessage> batch = jdbc.sql("""
                            SELECT id, aggregate_id, event_type, payload::text AS payload
                            FROM outbox_events
                            WHERE published_at IS NULL
                            ORDER BY id
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED""")
                    .param(batchSize)
                    .query((rs, n) -> new OutboxMessage(rs.getLong("id"), rs.getObject("aggregate_id", UUID.class),
                            rs.getString("event_type"), rs.getString("payload")))
                    .list();
            if (batch.isEmpty()) {
                return 0;
            }
            publisher.publish(batch);
            jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id = ANY(?)")
                    .param(batch.stream().map(OutboxMessage::id).toArray(Long[]::new))
                    .update();
            return batch.size();
        });
        return published == null ? 0 : published;
    }

    public long pendingCount() {
        return jdbc.sql("SELECT count(*) FROM outbox_events WHERE published_at IS NULL").query(Long.class).single();
    }
}
