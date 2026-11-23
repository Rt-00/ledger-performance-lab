package dev.rafaeltoledo.ledger.adapter.persistence;

import dev.rafaeltoledo.ledger.application.port.EventOutbox;
import dev.rafaeltoledo.ledger.domain.transfer.TransferCompleted;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Transactional outbox: the event row commits (or rolls back) together with the transfer. */
@Repository
class JdbcEventOutbox implements EventOutbox {

    static final String TRANSFER_COMPLETED = "TransferCompleted";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcEventOutbox(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void append(TransferCompleted event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", event.transferId().toString());
        payload.put("sourceAccountId", event.source().toString());
        payload.put("targetAccountId", event.target().toString());
        payload.put("amount", event.amount().toDecimal().toPlainString());
        payload.put("currency", event.amount().currency().getCurrencyCode());
        payload.put("occurredAt", event.occurredAt().toString());

        jdbc.sql("INSERT INTO outbox_events (aggregate_id, event_type, payload) VALUES (?, ?, ?::jsonb)")
                .params(event.transferId().value(), TRANSFER_COMPLETED, json.writeValueAsString(payload))
                .update();
    }
}
