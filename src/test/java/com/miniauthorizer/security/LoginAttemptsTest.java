package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoginAttemptsTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String LOCK_WARNING = "API user 'terminal' locked";

    @Mock
    private ApiUserRepository repository;

    private LoginAttempts loginAttempts() {
        return new LoginAttempts(repository,
                new ApiSecurityProperties(null, new ApiSecurityProperties.Lockout(3, Duration.ofMinutes(10))),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void countsAWrongPasswordAndThenLocksWhenTheLimitIsReached() {
        loginAttempts().onWrongPassword(wrongPassword());

        InOrder order = inOrder(repository);
        order.verify(repository).incrementFailedAttempts("terminal");
        order.verify(repository).lockWhenAttemptsReach("terminal", 3, NOW.plus(Duration.ofMinutes(10)));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void warnsWhenTheUserGetsLocked(CapturedOutput output) {
        when(repository.lockWhenAttemptsReach(anyString(), anyInt(), any())).thenReturn(1);

        loginAttempts().onWrongPassword(wrongPassword());

        assertThat(output.getOut()).contains(LOCK_WARNING + " for PT10M after 3 failed logins");
    }

    @Test
    void staysQuietWhileTheLimitIsNotReached(CapturedOutput output) {
        when(repository.lockWhenAttemptsReach(anyString(), anyInt(), any())).thenReturn(0);

        loginAttempts().onWrongPassword(wrongPassword());

        assertThat(output.getOut()).doesNotContain(LOCK_WARNING);
    }

    @Test
    void startsTheCountOverAfterASuccessfulLogin() {
        loginAttempts().onSuccessfulLogin(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated("terminal", "password", List.of())));

        verify(repository).resetFailedAttempts("terminal");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void doesNotTouchTheDatabaseWhenTheClientUsedABearerToken() {
        loginAttempts().onSuccessfulLogin(new AuthenticationSuccessEvent(
                new TestingAuthenticationToken("terminal", "eyJ.token")));

        verifyNoInteractions(repository);
    }

    private static AuthenticationFailureBadCredentialsEvent wrongPassword() {
        return new AuthenticationFailureBadCredentialsEvent(
                new TestingAuthenticationToken("terminal", "wrong"), new BadCredentialsException("Bad credentials"));
    }
}
