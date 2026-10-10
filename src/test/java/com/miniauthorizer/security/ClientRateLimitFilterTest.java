package com.miniauthorizer.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRateLimitFilterTest {

    private final ClientRateLimitFilter filter = new ClientRateLimitFilter(new RateLimitProperties(1, 2));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void letsAClientUseItsBurstAndThenAsksItToWait() throws Exception {
        authenticate("terminal");

        assertThat(call().getStatus()).isEqualTo(200);
        assertThat(call().getStatus()).isEqualTo(200);

        MockHttpServletResponse throttled = call();
        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isEqualTo("1");
        assertThat(throttled.getContentType()).isEqualTo("application/problem+json");
        assertThat(throttled.getContentAsString()).contains("\"status\":429");
    }

    @Test
    void neverPassesAThrottledRequestOn() throws Exception {
        authenticate("terminal");
        call();
        call();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("the request must not reach the API").isNull();
    }

    @Test
    void keepsASeparateBudgetForEachClient() throws Exception {
        authenticate("noisy");
        call();
        call();
        assertThat(call().getStatus()).isEqualTo(429);

        authenticate("quiet");
        assertThat(call().getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotCountAnonymousOrUnauthenticatedRequests() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(call().getStatus()).isEqualTo(200);
        assertThat(call().getStatus()).isEqualTo(200);
        assertThat(call().getStatus()).isEqualTo(200);

        Authentication notAuthenticated = new TestingAuthenticationToken("terminal", "secret");
        notAuthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(notAuthenticated);
        assertThat(call().getStatus()).isEqualTo(200);
        assertThat(call().getStatus()).isEqualTo(200);
        assertThat(call().getStatus()).isEqualTo(200);

        SecurityContextHolder.clearContext();
        assertThat(call().getStatus()).isEqualTo(200);
    }

    private static void authenticate(String client) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(client, "secret", "ROLE_API"));
    }

    private MockHttpServletResponse call() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        return response;
    }
}
