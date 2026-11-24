# ADR 0003: Money as `long` minor units

**Status:** accepted

## Decision
`Money(long minorUnits, Currency currency)`. Parsing validates the number of fraction digits
allowed by ISO 4217 for the currency (`10.255 BRL` is rejected). Arithmetic uses
`Math.addExact`/`subtractExact`, so overflow throws instead of wrapping around. The API exchanges
amounts as decimal strings (`"10.25"`), never as JSON numbers.

## Why not BigDecimal
- Exactness is the same for addition and subtraction, which is all a ledger needs.
- `long` maps 1:1 to `BIGINT`, needs no scale handling in SQL and does not allocate.
- The limit (about 92 quadrillion BRL) is far beyond any balance this system models.

## Consequences
Interest or FX calculations would need `BigDecimal` with an explicit rounding mode before
converting back to minor units.
