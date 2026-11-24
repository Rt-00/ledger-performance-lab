package dev.rafaeltoledo.ledger.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class StatementIndexTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    void entries_have_a_single_index_led_by_account_id() {
        List<String> indexes = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE tablename = 'ledger_entries'")
                .query(String.class).list();

        assertThat(indexes).singleElement().asString()
                .contains("ledger_entries_pkey")
                .contains("(account_id, id)");
    }

    @Test
    void statements_walk_the_primary_key_backwards_within_one_account() {
        // The test tables are tiny, so scans the planner would never pick on real volumes are disabled
        // for this transaction only.
        String plan = tx.execute(status -> {
            jdbc.sql("SET LOCAL enable_seqscan = off").update();
            jdbc.sql("SET LOCAL enable_bitmapscan = off").update();
            return String.join("\n", jdbc.sql("""
                            EXPLAIN SELECT id, transfer_id, direction, amount_minor, balance_after_minor, currency, occurred_at
                            FROM ledger_entries
                            WHERE account_id = '00000000-0000-7000-8000-000000000001' AND id < 100
                            ORDER BY id DESC LIMIT 51""")
                    .query(String.class).list());
        });

        assertThat(plan)
                .contains("Index Scan Backward using ledger_entries_pkey")
                .contains("Index Cond: ((account_id = '00000000-0000-7000-8000-000000000001'::uuid) AND (id < 100))");
    }
}
