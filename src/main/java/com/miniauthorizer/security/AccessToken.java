package com.miniauthorizer.security;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Successful token response (RFC 6749, section 5.1).
 */
public record AccessToken(
        @JsonProperty("access_token") String value,
        @JsonProperty("token_type") String type,
        @JsonProperty("expires_in") long expiresInSeconds,
        @JsonProperty("scope") String scope) {

    static AccessToken bearer(String value, long expiresInSeconds, String scope) {
        return new AccessToken(value, "Bearer", expiresInSeconds, scope);
    }

    @Override
    public String toString() {
        return "AccessToken[type=%s, expiresIn=%ds, scope=%s]".formatted(type, expiresInSeconds, scope);
    }
}
