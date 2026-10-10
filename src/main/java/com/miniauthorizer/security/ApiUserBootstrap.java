package com.miniauthorizer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the configured API user on startup. An existing user is left untouched: once it
 * exists, its password is managed in the database and a changed environment variable does
 * not silently reset it.
 */
@Component
@ConditionalOnProperty(prefix = "app.security.bootstrap-user", name = "username")
public class ApiUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiUserBootstrap.class);

    private final ApiUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ApiSecurityProperties.BootstrapUser bootstrapUser;

    public ApiUserBootstrap(ApiUserRepository repository, PasswordEncoder passwordEncoder,
                            ApiSecurityProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapUser = properties.bootstrapUser();
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.findById(bootstrapUser.username()).ifPresentOrElse(
                existing -> log.info("API user '{}' already exists", existing.getUsername()),
                this::create);
    }

    // Instances starting together may all try to insert; the primary key lets only one win.
    private void create() {
        try {
            repository.saveAndFlush(ApiUser.create(bootstrapUser.username(),
                    passwordEncoder.encode(bootstrapUser.password())));
            log.info("API user '{}' created", bootstrapUser.username());
        } catch (DataIntegrityViolationException createdByAnotherInstance) {
            log.info("API user '{}' was created by another instance", bootstrapUser.username());
        }
    }
}
