package com.miniauthorizer.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body of {@code POST /transacoes}. Field names follow the public API contract.
 * Card number and PIN are only checked for presence and size: an unknown card or a wrong PIN
 * must be answered with the contract's rejection codes, not with a validation error.
 */
public record TransactionRequest(
        @NotBlank(message = "is required")
        @Size(max = 19, message = "must have at most 19 characters")
        String numeroCartao,

        @NotBlank(message = "is required")
        @Size(max = 64, message = "must have at most 64 characters")
        String senhaCartao,

        @NotNull(message = "is required")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal valor) {

    Transaction toTransaction() {
        return new Transaction(numeroCartao, senhaCartao, valor);
    }
}
