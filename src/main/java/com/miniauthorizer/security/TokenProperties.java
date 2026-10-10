package com.miniauthorizer.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Access tokens issued by {@code POST /oauth2/token}.
 *
 * @param issuer          {@code iss} claim, also required on every token the API accepts
 * @param audience        {@code aud} claim, also required on every token the API accepts
 * @param timeToLive      token lifetime; short, because a stateless token cannot be revoked
 * @param signingKey      EC P-256 private key (PKCS#8 PEM); every instance must share it
 * @param verificationKey EC P-256 public key (PEM) matching the signing key
 */
@Validated
@ConfigurationProperties("app.security.tokens")
public record TokenProperties(
        @NotBlank @DefaultValue("mini-authorizer") String issuer,
        @NotBlank @DefaultValue("mini-authorizer") String audience,
        @NotNull @DefaultValue("5m") Duration timeToLive,
        Resource signingKey,
        Resource verificationKey) {
}
