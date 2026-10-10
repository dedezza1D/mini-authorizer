package com.miniauthorizer.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ApiSecurityProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    static final String BASIC_AUTHORITY = "ROLE_" + ApiUser.ROLE;
    static final String TOKEN_AUTHORITY = "SCOPE_" + TokenIssuer.SCOPE;

    // Takes the API username from the certificate subject, e.g. "CN=terminal-42,O=Acquirer".
    static final String CERTIFICATE_USERNAME = "CN=(.*?)(?:,|$)";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, UserDetailsService apiUsers,
                                           RateLimitProperties rateLimits) throws Exception {
        AuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint();

        return http
                // Every request carries its own credentials and no session or cookie is ever issued,
                // so there is no ambient authority for a cross-site request to abuse.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                // mTLS: a certificate validated by the TLS handshake identifies the client, which must
                // still be an enabled, unlocked API user. Only active where client-auth is enabled.
                .x509(x509 -> x509.subjectPrincipalRegex(CERTIFICATE_USERNAME).userDetailsService(apiUsers))
                // Bearer tokens are checked by signature, issuer, audience and expiry; an invalid one
                // gets the RFC 6750 challenge saying why.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Only the client's own credentials can obtain a token, never another token.
                        .requestMatchers(HttpMethod.POST, "/oauth2/token").hasRole(ApiUser.ROLE)
                        .anyRequest().hasAnyAuthority(BASIC_AUTHORITY, TOKEN_AUTHORITY))
                // Created here rather than as a bean, so Spring Boot does not also register it as a
                // servlet filter running before authentication.
                .addFilterAfter(new ClientRateLimitFilter(rateLimits), AuthorizationFilter.class)
                .build();
    }
}
