package dev.rafaeltoledo.ledger.adapter.web;

import dev.rafaeltoledo.ledger.domain.shared.DomainException;
import java.net.URI;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is an RFC 9457 Problem Details document with a stable {@code code} property, so that
 * clients branch on codes rather than on human-readable messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_REQUEST = "invalid-request";

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("invalid-amount", HttpStatus.BAD_REQUEST),
            Map.entry("invalid-idempotency-key", HttpStatus.BAD_REQUEST),
            Map.entry("invalid-page-request", HttpStatus.BAD_REQUEST),
            Map.entry("account-not-found", HttpStatus.NOT_FOUND),
            Map.entry("idempotency-conflict", HttpStatus.CONFLICT),
            Map.entry("transfer-contention", HttpStatus.CONFLICT));

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(e.code(), HttpStatus.UNPROCESSABLE_CONTENT);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if ("transfer-contention".equals(e.code())) {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(problem(status, e.code(), e.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST, e.getMessage()));
    }

    /** Framework-level 4xx errors (validation, missing header, malformed JSON...) get a code too. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && statusCode.is4xxClientError()) {
            problem.setType(URI.create("/problems/" + INVALID_REQUEST));
            problem.setProperty("code", INVALID_REQUEST);
        }
        return response;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("/problems/" + code));
        problem.setTitle(title(code));
        problem.setProperty("code", code);
        return problem;
    }

    private static String title(String code) {
        String words = code.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
