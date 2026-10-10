package com.miniauthorizer.security;

/**
 * Token request refused with one of the error codes of RFC 6749, section 5.2.
 */
public class OAuthErrorException extends RuntimeException {

    private final String error;

    OAuthErrorException(String error, String description) {
        super(description, null, false, false);
        this.error = error;
    }

    static OAuthErrorException invalidRequest(String description) {
        return new OAuthErrorException("invalid_request", description);
    }

    static OAuthErrorException unsupportedGrantType() {
        return new OAuthErrorException("unsupported_grant_type", "Only client_credentials is supported");
    }

    static OAuthErrorException invalidScope() {
        return new OAuthErrorException("invalid_scope", "The only scope available is '" + TokenIssuer.SCOPE + "'");
    }

    public String error() {
        return error;
    }
}
