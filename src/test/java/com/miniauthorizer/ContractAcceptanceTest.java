package com.miniauthorizer;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the acceptance script of the API contract over real HTTP, against the packaged security
 * configuration and MySQL: create a card, check its balance, spend it down to a rejection for
 * insufficient balance, then try a wrong PIN and an unknown card.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractAcceptanceTest extends MySqlIntegrationTest {

    private static final String CARD = "6549873025634501";
    private static final String PIN = "1234";

    @Autowired
    private TestRestTemplate anonymous;

    @Test
    void followsTheContractFromCardCreationToEveryRejection() {
        TestRestTemplate api = anonymous.withBasicAuth("username", "password");
        Map<String, String> newCard = Map.of("numeroCartao", CARD, "senha", PIN);

        ResponseEntity<String> created = api.postForEntity("/cartoes", newCard, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isEqualToIgnoringWhitespace("{\"senha\":\"1234\",\"numeroCartao\":\"6549873025634501\"}");

        assertThat(balance(api)).isEqualTo("500.00");

        BigDecimal expectedBalance = new BigDecimal("500.00");
        BigDecimal amount = new BigDecimal("10.00");
        for (int approved = 0; approved < 50; approved++) {
            ResponseEntity<String> transaction = transaction(api, CARD, PIN, amount);
            assertThat(transaction.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(transaction.getBody()).isEqualTo("OK");

            expectedBalance = expectedBalance.subtract(amount);
            assertThat(new BigDecimal(balance(api))).isEqualByComparingTo(expectedBalance);
        }

        assertRejected(transaction(api, CARD, PIN, amount), "SALDO_INSUFICIENTE");
        assertThat(balance(api)).isEqualTo("0.00");

        assertRejected(transaction(api, CARD, "0000", amount), "SENHA_INVALIDA");
        assertRejected(transaction(api, "1111222233334444", PIN, amount), "CARTAO_INEXISTENTE");

        ResponseEntity<String> duplicate = api.postForEntity("/cartoes", newCard, String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(duplicate.getBody()).isEqualToIgnoringWhitespace("{\"senha\":\"1234\",\"numeroCartao\":\"6549873025634501\"}");

        ResponseEntity<String> unknownBalance = api.getForEntity("/cartoes/1111222233334444", String.class);
        assertThat(unknownBalance.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownBalance.getBody()).isNull();
    }

    @Test
    void refusesEveryEndpointWithoutCredentials() {
        assertThat(anonymous.postForEntity("/cartoes", Map.of("numeroCartao", CARD, "senha", PIN), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getForEntity("/cartoes/" + CARD, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(transaction(anonymous, CARD, PIN, BigDecimal.ONE)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static String balance(TestRestTemplate api) {
        ResponseEntity<String> response = api.getForEntity("/cartoes/" + CARD, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static ResponseEntity<String> transaction(TestRestTemplate api, String card, String pin, BigDecimal amount) {
        return api.postForEntity("/transacoes",
                Map.of("numeroCartao", card, "senhaCartao", pin, "valor", amount), String.class);
    }

    private static void assertRejected(ResponseEntity<String> response, String code) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .matches(type -> type.isCompatibleWith(MediaType.TEXT_PLAIN));
        assertThat(response.getBody()).isEqualTo(code);
    }
}
