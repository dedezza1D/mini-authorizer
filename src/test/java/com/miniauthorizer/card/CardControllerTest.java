package com.miniauthorizer.card;

import com.miniauthorizer.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "API")
class CardControllerTest {

    private static final String NEW_CARD = """
            {"numeroCartao": "6549873025634501", "senha": "1234"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserDetailsService apiUsers;

    @MockitoBean
    private CardService cardService;

    @Test
    void createsCardAndEchoesItsData() throws Exception {
        when(cardService.issue("6549873025634501", "1234")).thenReturn(CardIssuance.CREATED);

        mvc.perform(post("/cartoes").contentType(MediaType.APPLICATION_JSON).content(NEW_CARD))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"senha": "1234", "numeroCartao": "6549873025634501"}
                        """, true));
    }

    @Test
    void answersUnprocessableEntityWithTheSameBodyWhenCardExists() throws Exception {
        when(cardService.issue("6549873025634501", "1234")).thenReturn(CardIssuance.ALREADY_EXISTS);

        mvc.perform(post("/cartoes").contentType(MediaType.APPLICATION_JSON).content(NEW_CARD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("""
                        {"senha": "1234", "numeroCartao": "6549873025634501"}
                        """, true));
    }

    @Test
    void rejectsMalformedCardDataListingEachInvalidField() throws Exception {
        mvc.perform(post("/cartoes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"numeroCartao": "12ab", "senha": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.numeroCartao").value("must have 13 to 19 digits"))
                .andExpect(jsonPath("$.errors.senha", allOf(
                        containsString("is required"), containsString("must have 4 to 6 digits"))));

        verifyNoInteractions(cardService);
    }

    @Test
    @WithAnonymousUser
    void challengesAnonymousCallersWithBasicAuth() throws Exception {
        mvc.perform(get("/cartoes/6549873025634501"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"mini-authorizer\""))
                .andExpect(content().string(""));

        verifyNoInteractions(cardService);
    }

    @Test
    void rejectsUnreadableBody() throws Exception {
        mvc.perform(post("/cartoes").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cardService);
    }

    @Test
    void returnsBalanceAsPlainNumber() throws Exception {
        when(cardService.balanceOf("6549873025634501")).thenReturn(Optional.of(new BigDecimal("495.15")));

        mvc.perform(get("/cartoes/6549873025634501"))
                .andExpect(status().isOk())
                .andExpect(content().string("495.15"));
    }

    @Test
    void keepsTwoDecimalPlacesForRoundBalances() throws Exception {
        when(cardService.balanceOf("6549873025634501")).thenReturn(Optional.of(new BigDecimal("500.00")));

        mvc.perform(get("/cartoes/6549873025634501"))
                .andExpect(content().string("500.00"));
    }

    @Test
    void answersNotFoundWithoutBodyForUnknownCard() throws Exception {
        when(cardService.balanceOf(any())).thenReturn(Optional.empty());

        mvc.perform(get("/cartoes/0000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(cardService).balanceOf("0000000000000000");
    }
}
