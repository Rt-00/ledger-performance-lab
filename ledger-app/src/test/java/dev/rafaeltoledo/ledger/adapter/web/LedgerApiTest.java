package dev.rafaeltoledo.ledger.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rafaeltoledo.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class LedgerApiTest {

    @Autowired
    MockMvcTester mvc;

    private String openAccount(String currency) {
        MvcTestResult result = mvc.post().uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"%s\"}".formatted(currency))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result.getResponse().getHeader("Location").substring("/accounts/".length());
    }

    private MvcTestResult deposit(String accountId, String amount, String key) {
        return mvc.post().uri("/accounts/{id}/deposits", accountId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"%s\"}".formatted(amount))
                .exchange();
    }

    private MvcTestResult transfer(String from, String to, String amount, String currency, String key) {
        return mvc.post().uri("/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceAccountId":"%s","targetAccountId":"%s","amount":"%s","currency":"%s"}"""
                        .formatted(from, to, amount, currency))
                .exchange();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Nested
    class Accounts {

        @Test
        void opens_and_reads_an_account() {
            String id = openAccount("BRL");

            assertThat(mvc.get().uri("/accounts/{id}", id)).hasStatusOk().bodyJson()
                    .isLenientlyEqualTo("""
                            {"id":"%s","type":"CUSTOMER","currency":"BRL","balance":"0.00","status":"ACTIVE"}"""
                            .formatted(id));
        }

        @Test
        void unknown_account_is_a_404_problem() {
            assertThat(mvc.get().uri("/accounts/{id}", UUID.randomUUID()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.code").isEqualTo("account-not-found");
        }

        @Test
        void malformed_ids_and_currencies_are_400() {
            assertThat(mvc.get().uri("/accounts/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(mvc.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"currency\":\"XX\"}"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(mvc.post().uri("/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
        }
    }

    @Nested
    class Transfers {

        @Test
        void deposit_then_transfer_updates_both_balances() {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");
            assertThat(deposit(alice, "100.00", key())).hasStatus(HttpStatus.CREATED);

            assertThat(transfer(alice, bob, "30.50", "BRL", key()))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().isLenientlyEqualTo("""
                            {"sourceAccountId":"%s","targetAccountId":"%s","amount":"30.50","currency":"BRL"}"""
                            .formatted(alice, bob));

            assertThat(mvc.get().uri("/accounts/{id}", alice)).bodyJson().extractingPath("$.balance").isEqualTo("69.50");
            assertThat(mvc.get().uri("/accounts/{id}", bob)).bodyJson().extractingPath("$.balance").isEqualTo("30.50");
        }

        @Test
        void retrying_with_the_same_key_replays_the_original_response() throws Exception {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");
            deposit(alice, "10.00", key());
            String key = key();

            MvcTestResult first = transfer(alice, bob, "1.00", "BRL", key);
            MvcTestResult retry = transfer(alice, bob, "1.00", "BRL", key);

            assertThat(first).hasStatus(HttpStatus.CREATED).doesNotContainHeader("Idempotent-Replayed");
            assertThat(retry).hasStatusOk().hasHeader("Idempotent-Replayed", "true");
            assertThat(retry).bodyJson().extractingPath("$.id")
                    .isEqualTo(com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id"));
            assertThat(mvc.get().uri("/accounts/{id}", alice)).bodyJson().extractingPath("$.balance").isEqualTo("9.00");
        }

        @Test
        void reusing_a_key_with_another_payload_is_a_409() {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");
            deposit(alice, "10.00", key());
            String key = key();
            transfer(alice, bob, "1.00", "BRL", key);

            assertThat(transfer(alice, bob, "2.00", "BRL", key))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("idempotency-conflict");
        }

        @Test
        void insufficient_funds_is_a_422_problem_with_a_stable_code() {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");

            MvcTestResult result = transfer(alice, bob, "1.00", "BRL", key());

            assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("insufficient-funds");
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/insufficient-funds");
            assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Insufficient funds");
        }

        @Test
        void transfer_without_idempotency_key_is_rejected() {
            assertThat(mvc.post().uri("/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                    {"sourceAccountId":"%s","targetAccountId":"%s","amount":"1.00","currency":"BRL"}"""
                    .formatted(UUID.randomUUID(), UUID.randomUUID())))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
        }

        @Test
        void invalid_amounts_and_keys_are_400() {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");

            assertThat(transfer(alice, bob, "1.001", "BRL", key())).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("invalid-amount");
            assertThat(transfer(alice, bob, "1.00", "BRL", "bad key")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("invalid-idempotency-key");
            assertThat(transfer(alice, bob, "1.00", "ZZZ", key())).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void currency_must_match_the_accounts() {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");
            deposit(alice, "10.00", key());

            assertThat(transfer(alice, bob, "1.00", "USD", key())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("currency-mismatch");
        }

        @Test
        void deposit_into_unknown_account_is_404() {
            assertThat(deposit(UUID.randomUUID().toString(), "1.00", key())).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Statements {

        @Test
        void pages_with_a_cursor_and_with_offset() throws Exception {
            String alice = openAccount("BRL");
            String bob = openAccount("BRL");
            deposit(alice, "100.00", key());
            for (int i = 1; i <= 3; i++) {
                transfer(alice, bob, i + ".00", "BRL", key());
            }

            MvcTestResult first = mvc.get().uri("/accounts/{id}/statement?limit=2", alice).exchange();
            assertThat(first).hasStatusOk().bodyJson().extractingPath("$.entries.length()").isEqualTo(2);
            assertThat(first).bodyJson().extractingPath("$.entries[0].direction").isEqualTo("DEBIT");
            assertThat(first).bodyJson().extractingPath("$.entries[0].balanceAfter").isEqualTo("94.00");
            Number cursor = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");

            assertThat(mvc.get().uri("/accounts/{id}/statement?limit=2&cursor={c}", alice, cursor))
                    .hasStatusOk().bodyJson().extractingPath("$.entries.length()").isEqualTo(2);
            assertThat(mvc.get().uri("/accounts/{id}/statement?limit=2&cursor={c}", alice, cursor))
                    .bodyJson().extractingPath("$.nextCursor").isNull();

            assertThat(mvc.get().uri("/accounts/{id}/statement?page=1&size=3", alice))
                    .hasStatusOk().bodyJson().extractingPath("$.entries.length()").isEqualTo(1);
        }

        @Test
        void page_size_is_bounded() {
            String alice = openAccount("BRL");

            assertThat(mvc.get().uri("/accounts/{id}/statement?limit=500", alice))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("invalid-page-request");
        }
    }
}
