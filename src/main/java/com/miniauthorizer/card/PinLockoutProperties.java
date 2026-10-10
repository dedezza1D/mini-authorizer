package com.miniauthorizer.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param maxAttempts wrong PINs within the window that block the card
 * @param window      how long wrong PINs keep counting, and how long the resulting block lasts
 */
@Validated
@ConfigurationProperties("app.cards.pin-lockout")
public record PinLockoutProperties(@Positive @DefaultValue("3") int maxAttempts,
                                   @NotNull @DefaultValue("30m") Duration window) {
}
