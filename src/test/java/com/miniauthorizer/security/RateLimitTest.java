package com.miniauthorizer.security;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.rate-limit.burst=3",
        "app.security.rate-limit.requests-per-second=1"
})
@AutoConfigureMockMvc
class RateLimitTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void throttlesAClientThatExceedsItsRateThroughTheRealFilterChain() throws Exception {
        String client = "client-" + UUID.randomUUID();
        users.saveAndFlush(ApiUser.create(client, passwordEncoder.encode("secret")));

        for (int request = 0; request < 3; request++) {
            mvc.perform(get("/cartoes/0000000000000000").with(httpBasic(client, "secret")))
                    .andExpect(status().isNotFound());
        }

        mvc.perform(get("/cartoes/0000000000000000").with(httpBasic(client, "secret")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void neverThrottlesTheHealthCheck() throws Exception {
        for (int request = 0; request < 10; request++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
