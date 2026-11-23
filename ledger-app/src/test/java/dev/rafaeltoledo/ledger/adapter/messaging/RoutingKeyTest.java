package dev.rafaeltoledo.ledger.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutingKeyTest {

    @Test
    void transfer_events_use_a_stable_topic_and_others_are_namespaced() {
        assertThat(RabbitEventPublisher.routingKey("TransferCompleted")).isEqualTo("transfer.completed");
        assertThat(RabbitEventPublisher.routingKey("AccountBlocked")).isEqualTo("ledger.accountblocked");
    }
}
