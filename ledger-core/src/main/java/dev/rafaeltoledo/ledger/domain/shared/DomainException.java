package dev.rafaeltoledo.ledger.domain.shared;

/**
 * Base type for every business rule violation. The {@link #code()} is a stable identifier that
 * adapters (e.g. the HTTP layer) can map to a response without parsing messages.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
