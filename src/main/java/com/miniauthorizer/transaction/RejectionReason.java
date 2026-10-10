package com.miniauthorizer.transaction;

/**
 * Why a transaction was not authorized. Each reason carries the code the API contract
 * returns to the card terminal.
 */
public enum RejectionReason {

    CARD_NOT_FOUND("CARTAO_INEXISTENTE"),
    INVALID_PIN("SENHA_INVALIDA"),
    INSUFFICIENT_BALANCE("SALDO_INSUFICIENTE");

    private final String code;

    RejectionReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
