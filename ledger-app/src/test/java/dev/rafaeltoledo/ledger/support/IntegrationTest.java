package dev.rafaeltoledo.ledger.support;

import dev.rafaeltoledo.ledger.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Boots the application against real PostgreSQL and RabbitMQ containers (one shared context). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
