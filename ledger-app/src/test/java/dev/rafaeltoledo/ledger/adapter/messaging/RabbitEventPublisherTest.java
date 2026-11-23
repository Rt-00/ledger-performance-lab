package dev.rafaeltoledo.ledger.adapter.messaging;

import static dev.rafaeltoledo.ledger.support.LedgerFixtures.brl;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.transfer.TransferId;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import dev.rafaeltoledo.ledger.support.IntegrationTest;
import dev.rafaeltoledo.ledger.support.LedgerFixtures;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RabbitEventPublisherTest {

    @Autowired OutboxRelay relay;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitTemplate rabbit;
    @Autowired TopicExchange ledgerEvents;
    @Autowired TransferFunds transferFunds;
    @Autowired LedgerFixtures fixtures;

    @Test
    void relayed_events_reach_a_bound_queue_with_metadata_for_deduplication() {
        // Durable and not auto-delete: receive() consumes and cancels (dropping auto-delete queues) and
        // RabbitMQ 4 no longer allows transient non-exclusive queues. Deleted at the end of the test.
        Queue queue = new Queue("test.transfer-events." + UUID.randomUUID(), true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(ledgerEvents).with("transfer.#"));
        AccountId a = fixtures.accountWith("10.00");
        AccountId b = fixtures.accountWith("0.00");
        TransferId id = transferFunds.execute(new TransferOrder(LedgerFixtures.randomKey(), a, b, brl("2.00")))
                .transfer().id();

        relay.publishPending();

        Message message = receiveUntil(queue, id, Duration.ofSeconds(10));
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getType()).isEqualTo("TransferCompleted");
        assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("transfer.completed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains("\"amount\": \"2.00\"");
        admin.deleteQueue(queue.getName());
    }

    private Message receiveUntil(Queue queue, TransferId id, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Message m = rabbit.receive(queue.getName(), 500);
            if (m != null && new String(m.getBody(), StandardCharsets.UTF_8).contains(id.toString())) {
                return m;
            }
        }
        throw new AssertionError("Event for transfer " + id + " was not delivered");
    }
}
