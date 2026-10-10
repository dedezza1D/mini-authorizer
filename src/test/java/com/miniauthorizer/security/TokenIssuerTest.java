package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issues tokens with the real encoder and checks them with the real decoder the API uses.
 */
class TokenIssuerTest {

    private static final TokenProperties PROPERTIES =
            new TokenProperties("mini-authorizer", "mini-authorizer", Duration.ofMinutes(5), null, null);
    private static final TokenKeys KEYS = TokenKeys.ephemeral();

    private final TokenConfig config = new TokenConfig();
    private final JwtEncoder encoder = config.jwtEncoder(KEYS);
    private final JwtDecoder decoder = config.jwtDecoder(KEYS, PROPERTIES);

    @Test
    void issuesAShortLivedTokenForTheClient() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        TokenIssuer issuer = new TokenIssuer(encoder, PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));

        AccessToken token = issuer.issueFor("terminal");
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.type()).isEqualTo("Bearer");
        assertThat(token.expiresInSeconds()).isEqualTo(300);
        assertThat(token.scope()).isEqualTo("api");
        assertThat(jwt.getSubject()).isEqualTo("terminal");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("mini-authorizer");
        assertThat(jwt.getAudience()).containsExactly("mini-authorizer");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("api");
        assertThat(jwt.getHeaders()).containsEntry("alg", "ES256");
    }

    @Test
    void givesEveryTokenItsOwnId() {
        TokenIssuer issuer = new TokenIssuer(encoder, PROPERTIES, Clock.systemUTC());

        assertThat(decoder.decode(issuer.issueFor("terminal").value()).getId())
                .isNotEqualTo(decoder.decode(issuer.issueFor("terminal").value()).getId());
    }

    @Test
    void neverPrintsTheTokenItself() {
        AccessToken token = new TokenIssuer(encoder, PROPERTIES, Clock.systemUTC()).issueFor("terminal");

        assertThat(token.toString()).doesNotContain(token.value()).contains("Bearer");
    }

    @Test
    void rejectsExpiredTokens() {
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        TokenIssuer pastIssuer = new TokenIssuer(encoder, PROPERTIES, Clock.fixed(longAgo, ZoneOffset.UTC));

        assertThatThrownBy(() -> decoder.decode(pastIssuer.issueFor("terminal").value()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        JwtEncoder forger = config.jwtEncoder(TokenKeys.ephemeral());
        TokenIssuer forgedIssuer = new TokenIssuer(forger, PROPERTIES, Clock.systemUTC());

        assertThatThrownBy(() -> decoder.decode(forgedIssuer.issueFor("terminal").value()))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsTokensFromAnotherIssuer() {
        assertThatThrownBy(() -> decoder.decode(token("someone-else", List.of("mini-authorizer"))))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void rejectsTokensMeantForAnotherService() {
        assertThatThrownBy(() -> decoder.decode(token("mini-authorizer", List.of("another-service"))))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("aud");
        assertThatThrownBy(() -> decoder.decode(token("mini-authorizer", null)))
                .isInstanceOf(JwtValidationException.class);
    }

    private String token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject("terminal").issuedAt(now).expiresAt(now.plusSeconds(60));
        Optional.ofNullable(audience).ifPresent(claims::audience);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), claims.build()))
                .getTokenValue();
    }
}
