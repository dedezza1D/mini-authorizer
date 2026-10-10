package com.miniauthorizer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class CryptoConfig {

    /**
     * BCrypt behind a delegating encoder: hashes are stored as {@code {bcrypt}...}, so the
     * algorithm or its cost can be upgraded later without invalidating existing hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
