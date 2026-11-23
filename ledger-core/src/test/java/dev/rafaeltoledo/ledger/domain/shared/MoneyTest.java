package dev.rafaeltoledo.ledger.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Nested
    class Creation {

        @Test
        void parses_decimal_amount_into_minor_units() {
            Money money = Money.of("10.25", BRL);

            assertThat(money.minorUnits()).isEqualTo(1025L);
            assertThat(money.currency()).isEqualTo(BRL);
        }

        @Test
        void respects_currencies_without_fraction_digits() {
            assertThat(Money.of("150", JPY).minorUnits()).isEqualTo(150L);
        }

        @Test
        void rejects_more_fraction_digits_than_the_currency_allows() {
            assertThatThrownBy(() -> Money.of("10.255", BRL))
                    .isInstanceOf(InvalidAmountException.class)
                    .hasMessageContaining("BRL");
        }

        @Test
        void rejects_null_currency() {
            assertThatThrownBy(() -> Money.ofMinor(1, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void zero_is_neither_positive_nor_negative() {
            Money zero = Money.zero(BRL);

            assertThat(zero.isPositive()).isFalse();
            assertThat(zero.isNegative()).isFalse();
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void adds_amounts_of_the_same_currency() {
            assertThat(Money.of("1.50", BRL).plus(Money.of("2.75", BRL))).isEqualTo(Money.of("4.25", BRL));
        }

        @Test
        void subtracts_and_can_go_negative() {
            Money result = Money.of("1.00", BRL).minus(Money.of("2.50", BRL));

            assertThat(result.minorUnits()).isEqualTo(-150L);
            assertThat(result.isNegative()).isTrue();
        }

        @Test
        void refuses_to_mix_currencies() {
            assertThatThrownBy(() -> Money.of("1.00", BRL).plus(Money.of("1.00", USD)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("BRL")
                    .hasMessageContaining("USD");
        }

        @Test
        void fails_loudly_on_overflow_instead_of_wrapping_around() {
            Money max = Money.ofMinor(Long.MAX_VALUE, BRL);

            assertThatThrownBy(() -> max.plus(Money.ofMinor(1, BRL))).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> Money.ofMinor(Long.MIN_VALUE, BRL).minus(Money.ofMinor(1, BRL)))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void compares_amounts() {
            Money ten = Money.of("10.00", BRL);

            assertThat(ten.isGreaterThanOrEqualTo(Money.of("10.00", BRL))).isTrue();
            assertThat(ten.isGreaterThanOrEqualTo(Money.of("10.01", BRL))).isFalse();
        }
    }

    @Test
    void converts_back_to_decimal_with_currency_scale() {
        assertThat(Money.ofMinor(1025, BRL).toDecimal()).isEqualTo(new BigDecimal("10.25"));
        assertThat(Money.ofMinor(5, BRL)).hasToString("BRL 0.05");
    }
}
