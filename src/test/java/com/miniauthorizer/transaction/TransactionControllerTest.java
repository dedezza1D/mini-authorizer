package com.miniauthorizer.transaction;

import com.miniauthorizer.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "API")
class TransactionControllerTest {

    private static final String TRANSACTION = """
            {"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": 10.00}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransactionAuthorizer authorizer;

    @MockitoBean
    private IdempotentTransactionAuthorizer idempotentAuthorizer;

    @Test
    void usesTheIdempotentFlowScopedToTheCallerWhenAKeyIsSent() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "nsu-000123")
                        .content(TRANSACTION))
                .andExpect(status().isCreated())
                .andExpect(content().string("OK"));

        verify(idempotentAuthorizer).authorize("user", new IdempotencyKey("nsu-000123"),
                new Transaction("6549873025634501", "1234", new BigDecimal("10.00")));
        verifyNoInteractions(authorizer);
    }

    @Test
    void keepsTheOriginalFlowWhenNoKeyIsSent() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isCreated());

        verifyNoInteractions(idempotentAuthorizer);
    }

    @Test
    void answersARepeatedRejectionLikeTheFirstOne() throws Exception {
        doThrow(new TransactionRejectedException(RejectionReason.INSUFFICIENT_BALANCE))
                .when(idempotentAuthorizer).authorize(any(), any(), any());

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "nsu-000123")
                        .content(TRANSACTION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("SALDO_INSUFICIENTE"));
    }

    @Test
    void rejectsMalformedIdempotencyKeys() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "nsu 123")
                        .content(TRANSACTION))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key must have 1 to 64 letters, digits, '-' or '_'"));

        verifyNoInteractions(authorizer, idempotentAuthorizer);
    }

    @Test
    void answersConflictWhenAKeyIsReusedForAnotherTransaction() throws Exception {
        doThrow(new IdempotencyKeyReusedException()).when(idempotentAuthorizer).authorize(any(), any(), any());

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "nsu-000123")
                        .content(TRANSACTION))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key was already used for a different transaction"));
    }

    @Test
    void answersCreatedWithOkWhenAuthorized() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("OK"));

        ArgumentCaptor<Transaction> authorized = ArgumentCaptor.forClass(Transaction.class);
        verify(authorizer).authorize(authorized.capture());
        assertThat(authorized.getValue().cardNumber()).isEqualTo("6549873025634501");
        assertThat(authorized.getValue().pin()).isEqualTo("1234");
        assertThat(authorized.getValue().amount()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest
    @CsvSource({
            "CARD_NOT_FOUND, CARTAO_INEXISTENTE",
            "INVALID_PIN, SENHA_INVALIDA",
            "INSUFFICIENT_BALANCE, SALDO_INSUFICIENTE"
    })
    void answersUnprocessableEntityWithTheContractCode(RejectionReason reason, String expectedBody) throws Exception {
        doThrow(new TransactionRejectedException(reason)).when(authorizer).authorize(any());

        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(expectedBody));
    }

    @Test
    void answersInPlainTextEvenWhenClientAsksForJson() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(TRANSACTION))
                .andExpect(status().isCreated())
                .andExpect(content().string("OK"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10.00", "10.001"})
    void rejectsInvalidAmountsBeforeAuthorizing(String amount) throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"numeroCartao": "6549873025634501", "senhaCartao": "1234", "valor": %s}
                        """.formatted(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.valor").exists());

        verifyNoInteractions(authorizer);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.numeroCartao").value("is required"))
                .andExpect(jsonPath("$.errors.senhaCartao").value("is required"))
                .andExpect(jsonPath("$.errors.valor").value("is required"));

        verifyNoInteractions(authorizer);
    }

    @Test
    void acceptsAnyPinFormatSoThatAWrongPinIsAnsweredByTheRules() throws Exception {
        mvc.perform(post("/transacoes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"numeroCartao": "123", "senhaCartao": "not-a-pin", "valor": 1}
                        """))
                .andExpect(status().isCreated());

        verify(authorizer).authorize(new Transaction("123", "not-a-pin", BigDecimal.ONE));
    }
}
