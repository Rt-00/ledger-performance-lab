package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.LockMode;
import java.util.Objects;

public record TransferSettings(LockMode lockMode, RetryPolicy retryPolicy) {

    public TransferSettings {
        Objects.requireNonNull(lockMode, "lockMode");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
    }
}
