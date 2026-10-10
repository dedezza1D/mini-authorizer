package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TokenController.class)
@Import(SecurityConfig.class)
class TokenControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TokenIssuer issuer;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(username = "terminal", roles = "API")
    void issuesATokenForTheAuthenticatedClientAndForbidsCachingIt() throws Exception {
        when(issuer.issueFor("terminal")).thenReturn(AccessToken.bearer("eyJ.token", 300, "api"));

        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.access_token").value("eyJ.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.scope").value("api"));
    }

    @Test
    @WithMockUser(username = "terminal", roles = "API")
    void acceptsTheOnlyScopeThereIs() throws Exception {
        when(issuer.issueFor("terminal")).thenReturn(AccessToken.bearer("eyJ.token", 300, "api"));

        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials").param("scope", "api"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "terminal", roles = "API")
    void refusesOtherGrantTypes() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"))
                .andExpect(jsonPath("$.error_description").value("Only client_credentials is supported"));

        verifyNoInteractions(issuer);
    }

    @Test
    @WithMockUser(username = "terminal", roles = "API")
    void requiresTheGrantType() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value("grant_type is required"));

        verifyNoInteractions(issuer);
    }

    @Test
    @WithMockUser(username = "terminal", roles = "API")
    void refusesUnknownScopes() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials").param("scope", "admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"))
                .andExpect(jsonPath("$.error_description").value("The only scope available is 'api'"));

        verifyNoInteractions(issuer);
    }

    @Test
    void neverExchangesATokenForAnotherToken() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .with(jwt().jwt((Jwt.Builder token) -> token.subject("terminal").claim("scope", "api"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(issuer);
    }

    @Test
    void challengesAnonymousCallersWithBasic() throws Exception {
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Basic"));

        verifyNoInteractions(issuer);
    }
}
