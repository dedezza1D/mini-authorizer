package com.miniauthorizer.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues short-lived ES256 access tokens for authenticated API clients.
 */
public class TokenIssuer {

    static final String SCOPE = "api";

    private final JwtEncoder encoder;
    private final TokenProperties properties;
    private final Clock clock;

    public TokenIssuer(JwtEncoder encoder, TokenProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueFor(String clientId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(clientId)
                .issuedAt(now)
                .expiresAt(now.plus(properties.timeToLive()))
                .id(UUID.randomUUID().toString())
                .claim("scope", SCOPE)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return AccessToken.bearer(token, properties.timeToLive().toSeconds(), SCOPE);
    }
}
