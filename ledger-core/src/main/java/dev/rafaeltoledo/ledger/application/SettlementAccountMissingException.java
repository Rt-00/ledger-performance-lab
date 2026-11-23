package dev.rafaeltoledo.ledger.application;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import java.util.Currency;

public final class SettlementAccountMissingException extends DomainException {

    public SettlementAccountMissingException(Currency currency) {
        super("settlement-account-missing", "No settlement account is configured for " + currency);
    }
}
