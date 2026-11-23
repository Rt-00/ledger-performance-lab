package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.domain.transfer.ExecutedTransfer;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import java.util.Optional;

public interface TransferRepository {

    Optional<Transfer> findByIdempotencyKey(IdempotencyKey key);

    /**
     * Stores the transfer and its ledger entries.
     *
     * @throws DuplicateIdempotencyKeyException if a transfer with the same key was committed concurrently
     */
    void add(ExecutedTransfer executed);
}
