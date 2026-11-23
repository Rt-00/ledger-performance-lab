package dev.rafaeltoledo.ledger.adapter.web;

import dev.rafaeltoledo.ledger.adapter.web.ApiModels.AccountResponse;
import dev.rafaeltoledo.ledger.adapter.web.ApiModels.DepositRequest;
import dev.rafaeltoledo.ledger.adapter.web.ApiModels.OpenAccountRequest;
import dev.rafaeltoledo.ledger.adapter.web.ApiModels.StatementResponse;
import dev.rafaeltoledo.ledger.adapter.web.ApiModels.TransferResponse;
import dev.rafaeltoledo.ledger.application.Deposit;
import dev.rafaeltoledo.ledger.application.GetAccount;
import dev.rafaeltoledo.ledger.application.GetStatement;
import dev.rafaeltoledo.ledger.application.OpenAccount;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final OpenAccount openAccount;
    private final GetAccount getAccount;
    private final Deposit deposit;
    private final GetStatement getStatement;

    AccountController(OpenAccount openAccount, GetAccount getAccount, Deposit deposit, GetStatement getStatement) {
        this.openAccount = openAccount;
        this.getAccount = getAccount;
        this.deposit = deposit;
        this.getStatement = getStatement;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = openAccount.execute(Currencies.parse(request.currency()));
        return ResponseEntity.created(URI.create("/accounts/" + account.id())).body(AccountResponse.of(account));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.of(getAccount.execute(AccountId.of(id)));
    }

    @PostMapping("/{id}/deposits")
    ResponseEntity<TransferResponse> deposit(
            @PathVariable UUID id,
            @RequestHeader(Idempotency.HEADER) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        AccountId accountId = AccountId.of(id);
        Money amount = Money.of(request.amount(), getAccount.execute(accountId).currency());
        return Idempotency.respond(deposit.execute(accountId, amount, new IdempotencyKey(idempotencyKey)));
    }

    /**
     * Keyset pagination by default ({@code limit} + {@code cursor}). Passing {@code page} switches to
     * OFFSET pagination, which is kept only so its cost can be compared (see docs/experiments).
     */
    @GetMapping("/{id}/statement")
    StatementResponse statement(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam Optional<Long> cursor,
            @RequestParam Optional<Integer> page,
            @RequestParam(defaultValue = "50") int size) {
        AccountId accountId = AccountId.of(id);
        return StatementResponse.of(page.isPresent()
                ? getStatement.byOffset(accountId, page.get(), size)
                : getStatement.byCursor(accountId, cursor, limit));
    }
}
