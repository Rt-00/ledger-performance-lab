package dev.rafaeltoledo.ledger.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.account.InsufficientFundsException;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MicrometerTransferObserverTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerTransferObserver observer = new MicrometerTransferObserver(registry);

    private double count(String outcome) {
        return registry.get("ledger.transfers").tag("outcome", outcome).counter().count();
    }

    @Test
    void counts_each_outcome_and_tags_rejections_with_the_error_code() {
        Money one = Money.of("1.00", Currency.getInstance("BRL"));
        observer.completed();
        observer.completed();
        observer.retried(1);
        observer.replayed();
        observer.rejected(new InsufficientFundsException(AccountId.newId(), one, one));

        assertThat(count("completed")).isEqualTo(2);
        assertThat(count("retried")).isEqualTo(1);
        assertThat(count("replayed")).isEqualTo(1);
        assertThat(registry.get("ledger.transfers").tag("outcome", "rejected").tag("reason", "insufficient-funds")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void rejections_that_are_not_domain_errors_get_a_generic_reason() {
        observer.rejected(new IllegalStateException("boom"));

        assertThat(registry.get("ledger.transfers").tag("reason", "unexpected").counter().count()).isEqualTo(1);
    }
}
