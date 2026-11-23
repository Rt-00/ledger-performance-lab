package dev.rafaeltoledo.ledger.domain.transfer;

import dev.rafaeltoledo.ledger.domain.account.Account;
import java.time.Instant;
import java.util.List;

/**
 * Domain service that moves money between two accounts with a double-entry posting.
 *
 * <p>It spans two aggregates, so it lives outside of {@link Account}. The credit side is validated
 * before anything is mutated and the debit (the only step that can still fail) runs first, so a
 * rejected transfer never leaves an account half-updated.
 */
public final class FundsTransfer {

    public ExecutedTransfer execute(TransferOrder order, Account source, Account target, Instant now) {
        if (order.source().equals(order.target())) {
            throw new SameAccountTransferException(order.source());
        }
        if (!source.id().equals(order.source()) || !target.id().equals(order.target())) {
            throw new IllegalArgumentException("Loaded accounts do not match the transfer order");
        }

        target.ensureCanBeCredited(order.amount());
        source.debit(order.amount());
        target.credit(order.amount());

        var transfer = new Transfer(
                TransferId.newId(), order.idempotencyKey(), source.id(), target.id(), order.amount(), now);
        var entries = List.of(
                new LedgerEntry(transfer.id(), source.id(), EntryDirection.DEBIT, order.amount(), source.balance(), now),
                new LedgerEntry(transfer.id(), target.id(), EntryDirection.CREDIT, order.amount(), target.balance(), now));
        return new ExecutedTransfer(transfer, entries);
    }
}
