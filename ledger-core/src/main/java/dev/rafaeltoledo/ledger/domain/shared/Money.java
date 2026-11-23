package dev.rafaeltoledo.ledger.domain.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money stored as a {@code long} of minor units (e.g. cents).
 *
 * <p>Using integer minor units instead of {@link BigDecimal} keeps arithmetic exact, allocation-free
 * on the hot path and maps 1:1 to a {@code BIGINT} column. Overflow is never silent: every operation
 * uses {@link Math#addExact}/{@link Math#subtractExact}.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(currency, "currency");
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(amount);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidAmountException("'%s' is not a valid amount".formatted(amount));
        }
        int scale = fractionDigits(currency);
        if (decimal.stripTrailingZeros().scale() > scale) {
            throw new InvalidAmountException(
                    "%s supports at most %d fraction digits, got %s".formatted(currency, scale, amount));
        }
        try {
            return new Money(decimal.movePointRight(scale).longValueExact(), currency);
        } catch (ArithmeticException e) {
            throw new InvalidAmountException("'%s' is out of range".formatted(amount));
        }
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return compareTo(other) >= 0;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits, fractionDigits(currency));
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + toDecimal().toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    private static int fractionDigits(Currency currency) {
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }
}
