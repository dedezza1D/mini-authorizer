package com.miniauthorizer.card;

import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * BCrypt over an HMAC-SHA256 of the card number and PIN, keyed with a secret (pepper) that is
 * never stored in the database.
 * <p>
 * A 4-digit PIN has only 10,000 values, so a stolen BCrypt hash alone falls to an offline search
 * in minutes. Without the pepper, which lives in the environment or a key management service,
 * a database dump is not enough to run that search. Binding the card number into the HMAC also
 * stops a hash copied from one card from validating on another.
 */
public class PepperedPinHasher implements PinHasher {

    static final int MINIMUM_PEPPER_BYTES = 32;
    private static final String HMAC = "HmacSHA256";

    private final SecretKeySpec pepper;
    private final PasswordEncoder passwordEncoder;

    public PepperedPinHasher(byte[] pepper, PasswordEncoder passwordEncoder) {
        this.pepper = new SecretKeySpec(Optional.ofNullable(pepper)
                .filter(key -> key.length >= MINIMUM_PEPPER_BYTES)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The PIN pepper must have at least " + MINIMUM_PEPPER_BYTES + " bytes")), HMAC);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    }

    @Override
    public String hash(String cardNumber, String pin) {
        return passwordEncoder.encode(peppered(cardNumber, pin));
    }

    @Override
    public boolean matches(String cardNumber, String pin, String storedHash) {
        return passwordEncoder.matches(peppered(cardNumber, pin), storedHash);
    }

    private String peppered(String cardNumber, String pin) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(pepper);
            byte[] digest = mac.doFinal((cardNumber + ':' + pin).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is not available", unavailable);
        }
    }
}
