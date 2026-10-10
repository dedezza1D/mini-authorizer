package com.miniauthorizer.card;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@RestController
@RequestMapping("/cartoes")
public class CardController {

    private static final Map<CardIssuance, HttpStatus> ISSUANCE_STATUS = new EnumMap<>(Map.of(
            CardIssuance.CREATED, HttpStatus.CREATED,
            CardIssuance.ALREADY_EXISTS, HttpStatus.UNPROCESSABLE_ENTITY));

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ResponseEntity<NewCardResponse> create(@Valid @RequestBody NewCardRequest request) {
        CardIssuance issuance = cardService.issue(request.numeroCartao(), request.senha());
        return ResponseEntity.status(ISSUANCE_STATUS.get(issuance)).body(NewCardResponse.echo(request));
    }

    @GetMapping("/{numeroCartao}")
    public ResponseEntity<BigDecimal> balance(@PathVariable("numeroCartao") String number) {
        return cardService.balanceOf(number)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
