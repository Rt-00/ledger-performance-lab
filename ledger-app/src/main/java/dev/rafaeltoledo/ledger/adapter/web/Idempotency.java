package dev.rafaeltoledo.ledger.adapter.web;

import dev.rafaeltoledo.ledger.adapter.web.ApiModels.TransferResponse;
import dev.rafaeltoledo.ledger.application.TransferResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** First execution answers 201; a replay of the same key answers 200 with the original transfer. */
final class Idempotency {

    static final String HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private Idempotency() {
    }

    static ResponseEntity<TransferResponse> respond(TransferResult result) {
        TransferResponse body = TransferResponse.of(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
