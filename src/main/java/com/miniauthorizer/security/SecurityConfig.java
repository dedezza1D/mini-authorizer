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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiSecurityProperties.class)
public class SecurityConfig {

    static final String BASIC_AUTHORITY = "ROLE_" + ApiUser.ROLE;
    static final String TOKEN_AUTHORITY = "SCOPE_" + TokenIssuer.SCOPE;

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
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
                .build();
    }
}
