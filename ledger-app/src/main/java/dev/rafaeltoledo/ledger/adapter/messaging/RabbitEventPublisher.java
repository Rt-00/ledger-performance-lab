package dev.rafaeltoledo.ledger.adapter.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Sends the whole batch on one channel and waits for publisher confirms once, not per message. */
class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbit;
    private final String exchange;
    private final Duration confirmTimeout;

    RabbitEventPublisher(RabbitTemplate rabbit, String exchange, Duration confirmTimeout) {
        this.rabbit = rabbit;
        this.exchange = exchange;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(List<OutboxMessage> batch) {
        rabbit.invoke(operations -> {
            for (OutboxMessage event : batch) {
                operations.send(exchange, routingKey(event.type()), toMessage(event));
            }
            operations.waitForConfirmsOrDie(confirmTimeout.toMillis());
            return null;
        });
    }

    static String routingKey(String eventType) {
        return "TransferCompleted".equals(eventType) ? "transfer.completed" : "ledger." + eventType.toLowerCase();
    }

    private static Message toMessage(OutboxMessage event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setType(event.type())
                .setMessageId(event.type() + ":" + event.aggregateId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
