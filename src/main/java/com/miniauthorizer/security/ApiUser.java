package com.miniauthorizer.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A client system allowed to call the API (a card terminal network, for instance).
 */
@Entity
@Table(name = "api_user")
public class ApiUser {

    static final String ROLE = "API";

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    // Both lockout fields are written only through conditional UPDATEs in ApiUserRepository,
    // so concurrent failed logins on several instances are all counted.
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ApiUser() {
    }

    private ApiUser(String username, String passwordHash) {
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.enabled = true;
    }

    public static ApiUser create(String username, String passwordHash) {
        return new ApiUser(username, passwordHash);
    }

    public void disable() {
        enabled = false;
    }

    public boolean isLockedAt(Instant instant) {
        return Optional.ofNullable(lockedUntil).filter(instant::isBefore).isPresent();
    }

    public String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    int getFailedAttempts() {
        return failedAttempts;
    }

    Optional<Instant> getLockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }

    UserDetails toUserDetails(Instant now) {
        return User.withUsername(username)
                .password(passwordHash)
                .disabled(!enabled)
                .accountLocked(isLockedAt(now))
                .roles(ROLE)
                .build();
    }
}
