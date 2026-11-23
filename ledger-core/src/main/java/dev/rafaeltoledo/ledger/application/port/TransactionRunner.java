package dev.rafaeltoledo.ledger.application.port;

import java.util.function.Supplier;

/** Runs work atomically: everything commits, or everything is rolled back if it throws. */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
