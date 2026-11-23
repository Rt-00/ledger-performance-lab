package dev.rafaeltoledo.ledger.adapter.messaging;

import java.util.List;

@FunctionalInterface
interface EventPublisher {

    /** Publishes the batch and returns only once the broker confirmed it; throws otherwise. */
    void publish(List<OutboxMessage> batch);
}
