package dev.rafaeltoledo.ledger.domain.shared;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates RFC 9562 version 7 UUIDs: a 48-bit Unix millisecond timestamp followed by random bits.
 *
 * <p>Because new ids are roughly increasing, inserts land on the right-most page of a B-tree index
 * instead of random pages, which keeps primary key indexes compact and cache friendly compared to
 * random (v4) UUIDs.
 */
public final class TimeOrderedUuid {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TimeOrderedUuid() {
    }

    public static UUID next() {
        return next(System.currentTimeMillis());
    }

    static UUID next(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16 | 0x7000L | randA;
        long lsb = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
