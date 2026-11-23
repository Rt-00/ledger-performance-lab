package dev.rafaeltoledo.ledger.adapter.metrics;

import dev.rafaeltoledo.ledger.application.TransferObserver;
import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Exposes {@code ledger_transfers_total{outcome=...}} so retries and contention show up in Prometheus. */
@Component
class MicrometerTransferObserver implements TransferObserver {

    private static final String METRIC = "ledger.transfers";

    private final MeterRegistry registry;
    private final Counter completed;
    private final Counter retried;
    private final Counter replayed;

    MicrometerTransferObserver(MeterRegistry registry) {
        this.registry = registry;
        this.completed = registry.counter(METRIC, "outcome", "completed");
        this.retried = registry.counter(METRIC, "outcome", "retried");
        this.replayed = registry.counter(METRIC, "outcome", "replayed");
    }

    @Override
    public void completed() {
        completed.increment();
    }

    @Override
    public void retried(int attempt) {
        retried.increment();
    }

    @Override
    public void replayed() {
        replayed.increment();
    }

    @Override
    public void rejected(RuntimeException reason) {
        String code = reason instanceof DomainException domain ? domain.code() : "unexpected";
        registry.counter(METRIC, "outcome", "rejected", "reason", code).increment();
    }
}
