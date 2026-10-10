package com.miniauthorizer.security;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest extends MySqlIntegrationTest {

    private static final String DISABLED_USER = "disabled-client";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiUserRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void disabledUser() {
        repository.findById(DISABLED_USER).orElseGet(() -> {
            ApiUser user = ApiUser.create(DISABLED_USER, passwordEncoder.encode("secret"));
            user.disable();
            return repository.saveAndFlush(user);
        });
    }

    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                Arguments.of(post("/cartoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCartao\": \"6549873025634501\", \"senha\": \"1234\"}")),
                Arguments.of(get("/cartoes/6549873025634501")),
                Arguments.of(post("/transacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCartao\": \"6549873025634501\", \"senhaCartao\": \"1234\", \"valor\": 10}")),
                Arguments.of(get("/actuator/env")));
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void requiresCredentialsOnEveryEndpoint(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, ApiAuthenticationEntryPoint.CHALLENGE))
                .andExpect(content().string(""));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic("username", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownUserWithTheSameAnswerAsAWrongPassword() throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic("nobody", "password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, ApiAuthenticationEntryPoint.CHALLENGE))
                .andExpect(content().string(""));
    }

    @Test
    void rejectsDisabledUserEvenWithTheRightPassword() throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic(DISABLED_USER, "secret")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void letsTheBootstrapUserInWithoutOpeningASession() throws Exception {
        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic("username", "password")))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void keepsHealthCheckPublicForLoadBalancers() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void storesTheBootstrapPasswordOnlyAsAHash() {
        ApiUser user = repository.findById("username").orElseThrow();

        assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("password");
        assertThat(passwordEncoder.matches("password", user.getPasswordHash())).isTrue();
    }
}
