package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.domain.transfer.TransferCompleted;

/** Stores events in the same transaction as the state change; a relay publishes them afterwards. */
public interface EventOutbox {

    void append(TransferCompleted event);
}
