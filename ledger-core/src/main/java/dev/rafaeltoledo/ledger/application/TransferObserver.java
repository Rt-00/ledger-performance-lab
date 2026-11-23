package dev.rafaeltoledo.ledger.application;

/** Hook for metrics: lets adapters count retries, replays and rejections without coupling the core to Micrometer. */
public interface TransferObserver {

    TransferObserver NO_OP = new TransferObserver() {
    };

    default void completed() {
    }

    default void retried(int attempt) {
    }

    default void replayed() {
    }

    default void rejected(RuntimeException reason) {
    }
}
