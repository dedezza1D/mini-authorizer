package com.miniauthorizer.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Objects;

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

    public String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    UserDetails toUserDetails() {
        return User.withUsername(username)
                .password(passwordHash)
                .disabled(!enabled)
                .roles(ROLE)
                .build();
    }
}
