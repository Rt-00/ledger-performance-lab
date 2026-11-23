package dev.rafaeltoledo.ledger.config;

import dev.rafaeltoledo.ledger.application.Deposit;
import dev.rafaeltoledo.ledger.application.GetAccount;
import dev.rafaeltoledo.ledger.application.GetStatement;
import dev.rafaeltoledo.ledger.application.OpenAccount;
import dev.rafaeltoledo.ledger.application.RetryPolicy;
import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.application.TransferObserver;
import dev.rafaeltoledo.ledger.application.TransferSettings;
import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.EventOutbox;
import dev.rafaeltoledo.ledger.application.port.StatementQuery;
import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import dev.rafaeltoledo.ledger.application.port.TransferRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The core has no Spring annotations; this is the only place where use cases become beans. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LedgerProperties.class)
class UseCaseConfiguration {

    /** PostgreSQL stores microseconds; ticking at that precision keeps in-memory and stored instants equal. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }

    @Bean
    TransferFunds transferFunds(AccountRepository accounts, TransferRepository transfers, EventOutbox outbox,
            TransactionRunner tx, Clock clock, LedgerProperties properties, ObjectProvider<TransferObserver> observer) {
        var retry = new RetryPolicy(properties.retry().maxAttempts(), properties.retry().baseBackoff(),
                RetryPolicy.Sleeper.THREAD_SLEEP);
        return new TransferFunds(accounts, transfers, outbox, tx, clock,
                new TransferSettings(properties.lockMode(), retry), observer.getIfAvailable(() -> TransferObserver.NO_OP));
    }

    @Bean
    Deposit deposit(AccountRepository accounts, TransferFunds transferFunds) {
        return new Deposit(accounts, transferFunds);
    }

    @Bean
    OpenAccount openAccount(AccountRepository accounts, TransactionRunner tx) {
        return new OpenAccount(accounts, tx);
    }

    @Bean
    GetAccount getAccount(AccountRepository accounts) {
        return new GetAccount(accounts);
    }

    @Bean
    GetStatement getStatement(AccountRepository accounts, StatementQuery statements) {
        return new GetStatement(accounts, statements);
    }
}
