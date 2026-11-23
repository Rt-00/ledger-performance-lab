package dev.rafaeltoledo.ledger.adapter.web;

import java.util.Currency;

final class Currencies {

    private Currencies() {
    }

    static Currency parse(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("Unknown currency '%s'".formatted(code));
        }
    }
}
