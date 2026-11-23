package dev.rafaeltoledo.ledger.adapter.persistence;

import dev.rafaeltoledo.ledger.application.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    SpringTransactionRunner(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
