package dev.rafaeltoledo.ledger.domain.transfer;

import java.util.List;

public record ExecutedTransfer(Transfer transfer, List<LedgerEntry> entries) {

    public ExecutedTransfer {
        entries = List.copyOf(entries);
    }

    public TransferCompleted completedEvent() {
        return new TransferCompleted(
                transfer.id(), transfer.source(), transfer.target(), transfer.amount(), transfer.occurredAt());
    }
}
