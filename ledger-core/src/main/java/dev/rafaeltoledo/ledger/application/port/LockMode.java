package dev.rafaeltoledo.ledger.application.port;

/**
 * How accounts are protected against concurrent transfers.
 * <ul>
 *   <li>{@code PESSIMISTIC}: rows are locked ({@code SELECT ... FOR UPDATE}) in a deterministic order,
 *       so concurrent transfers on the same account queue up and deadlocks cannot happen.</li>
 *   <li>{@code OPTIMISTIC}: rows are read without locks and the update checks the version; on
 *       conflict the whole transaction is retried with backoff.</li>
 * </ul>
 */
public enum LockMode {
    PESSIMISTIC,
    OPTIMISTIC
}
