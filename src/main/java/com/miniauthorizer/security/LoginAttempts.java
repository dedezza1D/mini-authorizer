package com.miniauthorizer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Temporarily locks an API user after too many consecutive wrong passwords, slowing down
 * password guessing. The counter lives in the database, so the limit holds across instances.
 * <p>
 * Attempts made while the user is locked fail before the password is checked and are not
 * counted, so a lock is never extended by the guesses it blocks.
 */
@Component
public class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);

    private final ApiUserRepository repository;
    private final ApiSecurityProperties.Lockout lockout;
    private final Clock clock;

    public LoginAttempts(ApiUserRepository repository, ApiSecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.lockout = properties.lockout();
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onWrongPassword(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        repository.incrementFailedAttempts(username);

        Optional.of(repository.lockWhenAttemptsReach(username, lockout.maxAttempts(),
                        clock.instant().plus(lockout.duration())))
                .filter(lockedRows -> lockedRows > 0)
                .ifPresent(locked -> log.warn("API user '{}' locked for {} after {} failed logins",
                        username, lockout.duration(), lockout.maxAttempts()));
    }

    @EventListener
    @Transactional
    public void onSuccessfulLogin(AuthenticationSuccessEvent event) {
        // Only a password login can follow failed attempts; bearer tokens must not cost a write per request.
        Optional.of(event.getAuthentication())
                .filter(UsernamePasswordAuthenticationToken.class::isInstance)
                .ifPresent(login -> repository.resetFailedAttempts(login.getName()));
    }
}
