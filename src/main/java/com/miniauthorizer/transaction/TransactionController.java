package com.miniauthorizer.transaction;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transacoes")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionAuthorizer authorizer;

    public TransactionController(TransactionAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @PostMapping
    public ResponseEntity<String> authorize(@Valid @RequestBody TransactionRequest request) {
        authorizer.authorize(request.toTransaction());
        return plainText(HttpStatus.CREATED, "OK");
    }

    @ExceptionHandler(TransactionRejectedException.class)
    ResponseEntity<String> rejected(TransactionRejectedException rejection) {
        log.info("Transaction rejected: {}", rejection.reason());
        return plainText(HttpStatus.UNPROCESSABLE_ENTITY, rejection.reason().code());
    }

    private static ResponseEntity<String> plainText(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
