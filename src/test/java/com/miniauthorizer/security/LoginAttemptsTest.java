package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LoginAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private ApiUserRepository repository;

    private LoginAttempts loginAttempts() {
        return new LoginAttempts(repository,
                new ApiSecurityProperties(null, new ApiSecurityProperties.Lockout(3, Duration.ofMinutes(10))),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void countsAWrongPasswordAndThenLocksWhenTheLimitIsReached() {
        loginAttempts().onWrongPassword(new AuthenticationFailureBadCredentialsEvent(
                new TestingAuthenticationToken("terminal", "wrong"), new BadCredentialsException("Bad credentials")));

        InOrder order = inOrder(repository);
        order.verify(repository).incrementFailedAttempts("terminal");
        order.verify(repository).lockWhenAttemptsReach("terminal", 3, NOW.plus(Duration.ofMinutes(10)));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void startsTheCountOverAfterASuccessfulLogin() {
        loginAttempts().onSuccessfulLogin(new AuthenticationSuccessEvent(
                new TestingAuthenticationToken("terminal", "password")));

        verify(repository).resetFailedAttempts("terminal");
        verifyNoMoreInteractions(repository);
    }
}
