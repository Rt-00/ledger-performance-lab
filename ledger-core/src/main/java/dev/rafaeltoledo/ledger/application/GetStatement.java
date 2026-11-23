package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.application.port.AccountRepository;
import dev.rafaeltoledo.ledger.application.port.StatementQuery;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import java.util.Optional;

public final class GetStatement {

    public static final int MAX_PAGE_SIZE = 200;

    private final AccountRepository accounts;
    private final StatementQuery query;

    public GetStatement(AccountRepository accounts, StatementQuery query) {
        this.accounts = accounts;
        this.query = query;
    }

    public StatementPage byCursor(AccountId accountId, Optional<Long> cursor, int limit) {
        validate(accountId, limit);
        return query.after(accountId, cursor, limit);
    }

    public StatementPage byOffset(AccountId accountId, int page, int size) {
        if (page < 0) {
            throw new InvalidPageRequestException("page must be >= 0");
        }
        validate(accountId, size);
        return query.offset(accountId, (long) page * size, size);
    }

    private void validate(AccountId accountId, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidPageRequestException("page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (accounts.findById(accountId).isEmpty()) {
            throw new AccountNotFoundException(accountId);
        }
    }
}
