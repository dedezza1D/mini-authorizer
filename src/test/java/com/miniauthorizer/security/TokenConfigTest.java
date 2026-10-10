package com.miniauthorizer.security;

import com.miniauthorizer.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class TokenConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TokenConfig.class, ClockConfig.class);

    @Test
    void signsWithTheConfiguredKeySoThatEveryInstanceAcceptsTheSameTokens() {
        context.withPropertyValues(
                        "app.security.tokens.signing-key=classpath:tokens/test-signing-key.pem",
                        "app.security.tokens.verification-key=classpath:tokens/test-verification-key.pem")
                .run(started -> assertThat(started.getBean(TokenKeys.class).verificationKey())
                        .isEqualTo(TokenKeys.fromPem(new ClassPathResource("tokens/test-signing-key.pem"),
                                new ClassPathResource("tokens/test-verification-key.pem")).verificationKey()));
    }

    @Test
    void fallsBackToAnEphemeralKeyWhenNoneIsConfigured() {
        context.run(started -> assertThat(started).hasSingleBean(TokenKeys.class).hasSingleBean(TokenIssuer.class));
    }

    @Test
    void leavesTokenValidationToAnExternalIdentityProviderWhenDisabled() {
        context.withPropertyValues("app.security.tokens.enabled=false")
                .run(started -> assertThat(started)
                        .doesNotHaveBean(TokenIssuer.class)
                        .doesNotHaveBean(TokenKeys.class));
    }
}
