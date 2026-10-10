package com.miniauthorizer.support;

import com.miniauthorizer.card.PepperedPinHasher;
import com.miniauthorizer.card.PinHasher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.nio.charset.StandardCharsets;

public final class TestPins {

    /** The real PIN hashing, with a pepper that only exists in tests. */
    public static final PinHasher HASHER = new PepperedPinHasher(
            "test-pepper-of-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8),
            PasswordEncoderFactories.createDelegatingPasswordEncoder());

    private TestPins() {
    }
}
