package com.miniauthorizer.security;

import com.jayway.jsonpath.JsonPath;
import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client credentials flow against the real filter chain, token issuer and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2ClientCredentialsTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void exchangesClientCredentialsForATokenThatOpensTheApi() throws Exception {
        String token = token("username", "password");

        mvc.perform(get("/cartoes/0000000000000000").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/transacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCartao\": \"0000000000000000\", \"senhaCartao\": \"1234\", \"valor\": 1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("CARTAO_INEXISTENTE"));
    }

    @Test
    void refusesTheTokenEndpointToWrongCredentials() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("username", "wrong"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void explainsWhyABearerTokenWasRefused() throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer error=\"invalid_token\"")));
    }

    @Test
    void refusesExpiredTokens() throws Exception {
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));

        mvc.perform(get("/cartoes/0000000000000000").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + mint("mini-authorizer", hourAgo, hourAgo.plus(Duration.ofMinutes(5)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesTokensIssuedForAnotherService() throws Exception {
        Instant now = Instant.now();

        mvc.perform(get("/cartoes/0000000000000000").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + mint("another-service", now, now.plus(Duration.ofMinutes(5)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void servesBearerRequestsWithoutWritingToTheDatabase() throws Exception {
        String username = "client-" + UUID.randomUUID();
        users.saveAndFlush(ApiUser.create(username, passwordEncoder.encode("secret")));
        String token = token(username, "secret");
        wrongPassword(username);
        wrongPassword(username);

        mvc.perform(get("/cartoes/0000000000000000").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(users.findById(username).orElseThrow().getFailedAttempts())
                .as("a bearer request must not reset the failed attempts of the password login")
                .isEqualTo(2);
    }

    private String token(String username, String password) throws Exception {
        String response = mvc.perform(post("/oauth2/token").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.access_token");
    }

    private void wrongPassword(String username) throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic(username, "wrong")))
                .andExpect(status().isUnauthorized());
    }

    private String mint(String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mini-authorizer").audience(List.of(audience)).subject("username")
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("scope", "api")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), claims))
                .getTokenValue();
    }
}
