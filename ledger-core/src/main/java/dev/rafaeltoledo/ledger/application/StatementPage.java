package dev.rafaeltoledo.ledger.application;

import java.util.List;
import java.util.Optional;

/** {@code nextCursor} is present when there may be more entries after this page. */
public record StatementPage(List<StatementLine> lines, Optional<Long> nextCursor) {

    public StatementPage {
        lines = List.copyOf(lines);
    }
}
