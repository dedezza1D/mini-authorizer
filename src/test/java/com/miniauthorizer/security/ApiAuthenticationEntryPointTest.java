package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthenticationEntryPointTest {

    @Test
    void challengesWithBasicAndAnswersUnauthorizedWithoutBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"mini-authorizer\"");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getErrorMessage()).isNull();
    }
}
