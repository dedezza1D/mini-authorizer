package com.miniauthorizer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers 401 with the Basic challenge and no body. The status is set directly instead of
 * through {@code sendError}, which would forward to the error page and add a body. The answer
 * is the same whatever went wrong (unknown user, wrong password, disabled or locked account),
 * so it reveals nothing about which usernames exist.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String CHALLENGE = "Basic realm=\"mini-authorizer\"";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
