package dev.rafaeltoledo.ledger.application.port;

import dev.rafaeltoledo.ledger.application.StatementPage;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import java.util.Optional;

/** Read side of the ledger: statements are served straight from the entries table. */
public interface StatementQuery {

    /** Keyset pagination: entries older than {@code cursor} (newest first). Cost does not grow with depth. */
    StatementPage after(AccountId accountId, Optional<Long> cursor, int limit);

    /** Classic OFFSET pagination, kept to measure how it degrades on deep pages. */
    StatementPage offset(AccountId accountId, long offset, int limit);
}
