package dev.rafaeltoledo.ledger.adapter.web;

/** Request-level validation failure that the domain never sees (e.g. an unknown currency code). */
class InvalidRequestException extends RuntimeException {

    InvalidRequestException(String message) {
        super(message);
    }
}
