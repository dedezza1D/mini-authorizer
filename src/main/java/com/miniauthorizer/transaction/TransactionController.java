package com.miniauthorizer.transaction;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Optional;

@RestController
@RequestMapping("/transacoes")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionAuthorizer authorizer;
    private final IdempotentTransactionAuthorizer idempotentAuthorizer;

    public TransactionController(TransactionAuthorizer authorizer, IdempotentTransactionAuthorizer idempotentAuthorizer) {
        this.authorizer = authorizer;
        this.idempotentAuthorizer = idempotentAuthorizer;
    }

    /**
     * The idempotency key is optional, so clients of the original contract keep working; terminals
     * that retry on timeouts should always send one.
     */
    @PostMapping
    public ResponseEntity<String> authorize(@Valid @RequestBody TransactionRequest request,
                                            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String key,
                                            Principal client) {
        Transaction transaction = request.toTransaction();
        Optional.ofNullable(key)
                .map(IdempotencyKey::new)
                .ifPresentOrElse(
                        idempotencyKey -> idempotentAuthorizer.authorize(client.getName(), idempotencyKey, transaction),
                        () -> authorizer.authorize(transaction));
        return plainText(HttpStatus.CREATED, "OK");
    }

    @ExceptionHandler(TransactionRejectedException.class)
    ResponseEntity<String> rejected(TransactionRejectedException rejection) {
        log.info("Transaction rejected: {}", rejection.reason());
        return plainText(HttpStatus.UNPROCESSABLE_ENTITY, rejection.reason().code());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ProblemDetail invalidKey(InvalidIdempotencyKeyException invalid) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail reusedKey(IdempotencyKeyReusedException reused) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, reused.getMessage());
    }

    private static ResponseEntity<String> plainText(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
