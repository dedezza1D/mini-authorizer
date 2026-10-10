package com.miniauthorizer.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body of {@code POST /cartoes}. Field names follow the public API contract.
 */
public record NewCardRequest(
        @NotBlank(message = "is required")
        @Pattern(regexp = "\\d{13,19}", message = "must have 13 to 19 digits")
        String numeroCartao,

        @NotBlank(message = "is required")
        @Pattern(regexp = "\\d{4,6}", message = "must have 4 to 6 digits")
        String senha) {
}
