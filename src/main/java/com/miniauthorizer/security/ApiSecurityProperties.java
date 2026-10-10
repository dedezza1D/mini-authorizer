package com.miniauthorizer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapUser API user created on startup when it does not exist yet, so a fresh
 *                      environment is usable without a manual insert. Its credentials come
 *                      from the environment and are never logged.
 */
@ConfigurationProperties("app.security")
public record ApiSecurityProperties(BootstrapUser bootstrapUser) {

    public record BootstrapUser(String username, String password) {

        @Override
        public String toString() {
            return "BootstrapUser[username=%s]".formatted(username);
        }
    }
}
