package com.miniauthorizer.card;

/**
 * Response body of {@code POST /cartoes}: the contract echoes the submitted card data,
 * both when the card is created and when it already exists.
 */
public record NewCardResponse(String senha, String numeroCartao) {

    static NewCardResponse echo(NewCardRequest request) {
        return new NewCardResponse(request.senha(), request.numeroCartao());
    }
}
