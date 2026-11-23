package dev.rafaeltoledo.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerApplicationTest {

    @Test
    void contextLoads() {
        // Boots the whole application against real PostgreSQL and RabbitMQ containers.
    }
}
