package dev.rafaeltoledo.ledger.domain.transfer;

import java.util.regex.Pattern;

/** Client-supplied key that makes retrying a transfer safe: the same key never moves money twice. */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 100;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:\\-]+");

    public IdempotencyKey {
        if (value == null || value.isEmpty()) {
            throw new InvalidIdempotencyKeyException("Idempotency key is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException("Idempotency key must have at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidIdempotencyKeyException("Idempotency key may only contain letters, digits and . _ : -");
        }
    }
}
