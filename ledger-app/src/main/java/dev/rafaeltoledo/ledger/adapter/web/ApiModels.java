package dev.rafaeltoledo.ledger.adapter.web;

import dev.rafaeltoledo.ledger.application.StatementLine;
import dev.rafaeltoledo.ledger.application.StatementPage;
import dev.rafaeltoledo.ledger.domain.account.Account;
import dev.rafaeltoledo.ledger.domain.transfer.Transfer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response bodies. Amounts travel as decimal strings ("10.25") so that no client ever
 * parses money into a binary floating point number.
 */
final class ApiModels {

    private ApiModels() {
    }

    record OpenAccountRequest(@NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    record DepositRequest(@NotBlank String amount) {
    }

    record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID targetAccountId,
            @NotBlank String amount,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    record AccountResponse(UUID id, String type, String currency, String balance, String status) {

        static AccountResponse of(Account a) {
            return new AccountResponse(a.id().value(), a.type().name(), a.currency().getCurrencyCode(),
                    a.balance().toDecimal().toPlainString(), a.status().name());
        }
    }

    record TransferResponse(
            UUID id, String idempotencyKey, UUID sourceAccountId, UUID targetAccountId,
            String amount, String currency, Instant occurredAt) {

        static TransferResponse of(Transfer t) {
            return new TransferResponse(t.id().value(), t.idempotencyKey().value(), t.source().value(),
                    t.target().value(), t.amount().toDecimal().toPlainString(),
                    t.amount().currency().getCurrencyCode(), t.occurredAt());
        }
    }

    record StatementEntryResponse(
            long entryId, UUID transferId, String direction, String amount, String balanceAfter,
            String currency, Instant occurredAt) {

        static StatementEntryResponse of(StatementLine l) {
            return new StatementEntryResponse(l.entryId(), l.transferId().value(), l.direction().name(),
                    l.amount().toDecimal().toPlainString(), l.balanceAfter().toDecimal().toPlainString(),
                    l.amount().currency().getCurrencyCode(), l.occurredAt());
        }
    }

    record StatementResponse(List<StatementEntryResponse> entries, Long nextCursor) {

        static StatementResponse of(StatementPage page) {
            return new StatementResponse(page.lines().stream().map(StatementEntryResponse::of).toList(),
                    page.nextCursor().orElse(null));
        }
    }
}
