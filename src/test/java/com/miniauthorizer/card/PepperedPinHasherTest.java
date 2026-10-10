package com.miniauthorizer.card;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PepperedPinHasherTest {

    private static final String CARD = "6549873025634501";
    private static final PasswordEncoder BCRYPT = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final PinHasher hasher = new PepperedPinHasher(pepper('a'), BCRYPT);

    @Test
    void verifiesTheRightPinAndRejectsAnyOther() {
        String hash = hasher.hash(CARD, "1234");

        assertThat(hasher.matches(CARD, "1234", hash)).isTrue();
        assertThat(hasher.matches(CARD, "1235", hash)).isFalse();
    }

    @Test
    void storesABcryptHashThatIsUselessWithoutThePepper() {
        String hash = hasher.hash(CARD, "1234");

        assertThat(hash).startsWith("{bcrypt}").doesNotContain("1234");
        assertThat(BCRYPT.matches("1234", hash))
                .as("a database dump alone must not allow guessing the PIN")
                .isFalse();
        assertThat(new PepperedPinHasher(pepper('b'), BCRYPT).matches(CARD, "1234", hash))
                .as("a different pepper must not verify the hash")
                .isFalse();
    }

    @Test
    void bindsTheHashToItsCard() {
        String hash = hasher.hash(CARD, "1234");

        assertThat(hasher.matches("6549873025634502", "1234", hash))
                .as("a hash copied to another card must not validate there")
                .isFalse();
    }

    @Test
    void requiresAPepperOfAtLeastThirtyTwoBytes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PepperedPinHasher(new byte[31], BCRYPT))
                .withMessage("The PIN pepper must have at least 32 bytes");
        assertThatIllegalArgumentException().isThrownBy(() -> new PepperedPinHasher(null, BCRYPT));
        assertThat(new PepperedPinHasher(new byte[32], BCRYPT).hash(CARD, "1234")).isNotBlank();
    }

    private static byte[] pepper(char fill) {
        char[] chars = new char[32];
        Arrays.fill(chars, fill);
        return new String(chars).getBytes(StandardCharsets.UTF_8);
    }
}
