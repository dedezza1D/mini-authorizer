package com.miniauthorizer.config;

import com.miniauthorizer.card.PepperedPinHasher;
import com.miniauthorizer.card.PinHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;

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

    @Bean
    public PinHasher pinHasher(@Value("${app.security.pin-pepper}") String base64Pepper, PasswordEncoder passwordEncoder) {
        return new PepperedPinHasher(Base64.getDecoder().decode(base64Pepper), passwordEncoder);
    }
}
