package dev.rafaeltoledo.ledger.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.application.TransferContentionException;
import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;

class ProblemDetailsMappingTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void contention_is_a_409_with_retry_after() {
        ResponseEntity<ProblemDetail> response = handler.domain(new TransferContentionException(5, new RuntimeException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
    }

    @Test
    void unmapped_domain_errors_default_to_422() {
        DomainException unknown = new DomainException("something-new", "Something new") { };

        ResponseEntity<ProblemDetail> response = handler.domain(unknown);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().getTitle()).isEqualTo("Something new");
        assertThat(response.getBody().getProperties()).containsEntry("code", "something-new");
    }
}
