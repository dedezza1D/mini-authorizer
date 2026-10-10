package com.miniauthorizer.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param bootstrapUser API user created on startup when it does not exist yet, so a fresh
 *                      environment is usable without a manual insert. Its credentials come
 *                      from the environment and are never logged.
 * @param lockout       how many consecutive failed logins lock an API user, and for how long
 */
@Validated
@ConfigurationProperties("app.security")
public record ApiSecurityProperties(BootstrapUser bootstrapUser, @Valid @DefaultValue Lockout lockout) {

    public record BootstrapUser(String username, String password) {

        @Override
        public String toString() {
            return "BootstrapUser[username=%s]".formatted(username);
        }
    }

    public record Lockout(@Positive @DefaultValue("5") int maxAttempts,
                          @NotNull @DefaultValue("15m") Duration duration) {
    }
}
