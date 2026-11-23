package dev.rafaeltoledo.ledger.adapter.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    static final String EXCHANGE = "ledger.events";

    @Bean
    TopicExchange ledgerEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, TransactionTemplate tx, RabbitTemplate rabbit, MeterRegistry meters,
            @Value("${ledger.outbox.relay.batch-size:500}") int batchSize,
            @Value("${ledger.outbox.relay.confirm-timeout:5s}") Duration confirmTimeout) {
        OutboxRelay relay = new OutboxRelay(jdbc, tx, new RabbitEventPublisher(rabbit, EXCHANGE, confirmTimeout), batchSize);
        Gauge.builder("ledger.outbox.pending", relay, OutboxRelay::pendingCount)
                .description("Events committed but not yet confirmed by the broker")
                .register(meters);
        return relay;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnBooleanProperty(name = "ledger.outbox.relay.enabled", matchIfMissing = true)
    static class RelaySchedulingConfiguration {
    }
}
