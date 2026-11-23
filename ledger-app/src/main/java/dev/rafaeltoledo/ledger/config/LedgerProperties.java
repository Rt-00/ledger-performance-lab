package dev.rafaeltoledo.ledger.config;

import dev.rafaeltoledo.ledger.application.port.LockMode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledger")
public record LedgerProperties(@DefaultValue("PESSIMISTIC") LockMode lockMode, @DefaultValue Retry retry) {

    public record Retry(@DefaultValue("5") int maxAttempts, @DefaultValue("5ms") Duration baseBackoff) {
    }
}
