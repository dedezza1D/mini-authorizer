package com.miniauthorizer.security;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Requests each authenticated API client may make, per application instance.
 *
 * @param requestsPerSecond sustained rate, refilled continuously
 * @param burst             requests allowed at once after a quiet period
 */
@Validated
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        @Positive @DefaultValue("100") long requestsPerSecond,
        @Positive @DefaultValue("200") long burst) {
}
