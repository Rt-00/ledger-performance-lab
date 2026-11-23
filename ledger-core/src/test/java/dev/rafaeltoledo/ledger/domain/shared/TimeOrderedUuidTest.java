package dev.rafaeltoledo.ledger.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeOrderedUuidTest {

    @Test
    void produces_version_7_rfc_variant_uuids() {
        UUID uuid = TimeOrderedUuid.next();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void embeds_the_timestamp_so_later_ids_sort_after_earlier_ones() {
        UUID earlier = TimeOrderedUuid.next(1_700_000_000_000L);
        UUID later = TimeOrderedUuid.next(1_700_000_000_001L);

        assertThat(earlier.getMostSignificantBits() >>> 16).isEqualTo(1_700_000_000_000L);
        assertThat(later).isGreaterThan(earlier);
    }
}
