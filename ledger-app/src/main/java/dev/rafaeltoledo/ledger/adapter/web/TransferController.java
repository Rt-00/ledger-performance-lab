package dev.rafaeltoledo.ledger.adapter.web;

import dev.rafaeltoledo.ledger.adapter.web.ApiModels.TransferRequest;
import dev.rafaeltoledo.ledger.adapter.web.ApiModels.TransferResponse;
import dev.rafaeltoledo.ledger.application.TransferFunds;
import dev.rafaeltoledo.ledger.domain.account.AccountId;
import dev.rafaeltoledo.ledger.domain.shared.Money;
import dev.rafaeltoledo.ledger.domain.transfer.IdempotencyKey;
import dev.rafaeltoledo.ledger.domain.transfer.TransferOrder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TransferController {

    private final TransferFunds transferFunds;

    TransferController(TransferFunds transferFunds) {
        this.transferFunds = transferFunds;
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResponse> transfer(
            @RequestHeader(Idempotency.HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferOrder order = new TransferOrder(
                new IdempotencyKey(idempotencyKey),
                AccountId.of(request.sourceAccountId()),
                AccountId.of(request.targetAccountId()),
                Money.of(request.amount(), Currencies.parse(request.currency())));
        return Idempotency.respond(transferFunds.execute(order));
    }
}
