package dev.rafaeltoledo.ledger.domain.account;

import dev.rafaeltoledo.ledger.domain.shared.TimeOrderedUuid;
import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) implements Comparable<AccountId> {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(TimeOrderedUuid.next());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
