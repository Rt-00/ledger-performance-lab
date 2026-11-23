package dev.rafaeltoledo.ledger.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "order-123", "0198f2c4-7c1e-7d4b-9a37-2f1e7c0b9d11", "client:batch_9.part-1"})
    void accepts_url_safe_keys(String value) {
        assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "has space", "semi;colon", "ação"})
    void rejects_blank_or_unsafe_keys(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(DomainException.class);
    }

    @org.junit.jupiter.api.Test
    void rejects_keys_longer_than_100_characters() {
        assertThatThrownBy(() -> new IdempotencyKey("x".repeat(101)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(new IdempotencyKey("x".repeat(100)).value()).hasSize(100);
    }
}
