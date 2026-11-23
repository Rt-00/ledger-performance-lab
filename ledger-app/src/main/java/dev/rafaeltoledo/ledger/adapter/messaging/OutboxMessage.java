package dev.rafaeltoledo.ledger.adapter.messaging;

import java.util.UUID;

record OutboxMessage(long id, UUID aggregateId, String type, String payload) {
}
