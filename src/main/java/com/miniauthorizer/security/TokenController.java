package com.miniauthorizer.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth2 token endpoint for the client credentials grant (RFC 6749, section 4.4). The client
 * authenticates with HTTP Basic once, then calls the API with the short-lived bearer token, which
 * is verified by signature alone: no database lookup and no BCrypt check per request.
 */
@RestController
@ConditionalOnProperty(prefix = "app.security.tokens", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenController {

    private static final String CLIENT_CREDENTIALS = "client_credentials";

    private final TokenIssuer issuer;

    public TokenController(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<AccessToken> token(@RequestParam(name = "grant_type", required = false) String grantType,
                                             @RequestParam(name = "scope", required = false) String scope,
                                             Principal client) {
        String requestedGrant = Optional.ofNullable(grantType)
                .orElseThrow(() -> OAuthErrorException.invalidRequest("grant_type is required"));
        Optional.of(requestedGrant)
                .filter(CLIENT_CREDENTIALS::equals)
                .orElseThrow(OAuthErrorException::unsupportedGrantType);
        Optional.ofNullable(scope)
                .filter(requested -> !TokenIssuer.SCOPE.equals(requested))
                .ifPresent(unknown -> {
                    throw OAuthErrorException.invalidScope();
                });

        return noStore(ResponseEntity.ok()).body(issuer.issueFor(client.getName()));
    }

    @ExceptionHandler(OAuthErrorException.class)
    ResponseEntity<Map<String, String>> refused(OAuthErrorException refusal) {
        return noStore(ResponseEntity.badRequest())
                .body(Map.of("error", refusal.error(), "error_description", refusal.getMessage()));
    }

    // Tokens are credentials: RFC 6749 forbids caches from keeping token responses.
    private static ResponseEntity.BodyBuilder noStore(ResponseEntity.BodyBuilder response) {
        return response.cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache");
    }
}
