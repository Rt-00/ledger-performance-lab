package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.shared.TimeOrderedUuid;
import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {

    public TransferId {
        Objects.requireNonNull(value, "value");
    }

    public static TransferId newId() {
        return new TransferId(TimeOrderedUuid.next());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
