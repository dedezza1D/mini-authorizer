package com.miniauthorizer.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Built-in token issuer, on by default so the project runs on its own. Where an identity provider
 * (Keycloak, Cognito, Entra ID) issues the tokens, set {@code app.security.tokens.enabled=false} and
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}: the API then only validates them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenProperties.class)
@ConditionalOnProperty(prefix = "app.security.tokens", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenConfig {

    private static final Logger log = LoggerFactory.getLogger(TokenConfig.class);

    @Bean
    @ConditionalOnExpression("'${app.security.tokens.signing-key:}' != ''")
    public TokenKeys configuredTokenKeys(TokenProperties properties) {
        return TokenKeys.fromPem(properties.signingKey(), properties.verificationKey());
    }

    @Bean
    @ConditionalOnExpression("'${app.security.tokens.signing-key:}' == ''")
    public TokenKeys ephemeralTokenKeys() {
        log.warn("No token signing key configured: using an ephemeral key, valid only on this instance until restart");
        return TokenKeys.ephemeral();
    }

    @Bean
    public JwtEncoder jwtEncoder(TokenKeys keys) {
        ECKey jwk = new ECKey.Builder(Curve.P_256, keys.verificationKey()).privateKey(keys.signingKey()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(TokenKeys keys, TokenProperties properties) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256,
                new ImmutableJWKSet<>(new JWKSet(new ECKey.Builder(Curve.P_256, keys.verificationKey()).build()))));
        // Claims are checked below by Spring's validators, which report failures as RFC 6750 errors.
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                audienceValidator(properties.audience())));
        return decoder;
    }

    @Bean
    public TokenIssuer tokenIssuer(JwtEncoder encoder, TokenProperties properties, Clock clock) {
        return new TokenIssuer(encoder, properties, clock);
    }

    // A token minted by the same issuer for another service must not open this API.
    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                audiences -> Optional.ofNullable(audiences).filter(values -> values.contains(audience)).isPresent());
    }
}
