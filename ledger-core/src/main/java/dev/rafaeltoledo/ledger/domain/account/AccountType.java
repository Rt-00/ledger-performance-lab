package dev.rafaeltoledo.ledger.domain.account;

/**
 * CUSTOMER accounts can never be overdrawn. SETTLEMENT accounts are internal contra accounts used
 * to bring money into the ledger (deposits) and are allowed to go negative, so that the sum of all
 * balances in a currency is always zero.
 */
public enum AccountType {
    CUSTOMER,
    SETTLEMENT;

    boolean allowsNegativeBalance() {
        return this == SETTLEMENT;
    }
}
