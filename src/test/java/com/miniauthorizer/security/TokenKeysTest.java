package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenKeysTest {

    private static final ClassPathResource SIGNING_KEY = new ClassPathResource("tokens/test-signing-key.pem");
    private static final ClassPathResource VERIFICATION_KEY = new ClassPathResource("tokens/test-verification-key.pem");

    @Test
    void loadsAMatchingP256KeyPairFromPem() throws Exception {
        TokenKeys keys = TokenKeys.fromPem(SIGNING_KEY, VERIFICATION_KEY);

        assertThat(keys.verificationKey().getParams().getCurve().getField().getFieldSize()).isEqualTo(256);
        assertThat(signedAndVerified(keys)).isTrue();
    }

    @Test
    void generatesADifferentEphemeralKeyPairEachTime() throws Exception {
        TokenKeys first = TokenKeys.ephemeral();
        TokenKeys second = TokenKeys.ephemeral();

        assertThat(signedAndVerified(first)).isTrue();
        assertThat(first.verificationKey()).isNotEqualTo(second.verificationKey());
    }

    @Test
    void rejectsSomethingThatIsNotAnEcKey() {
        ByteArrayResource garbage = new ByteArrayResource(
                "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----".getBytes(StandardCharsets.US_ASCII));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TokenKeys.fromPem(garbage, VERIFICATION_KEY))
                .withMessage("Invalid EC P-256 token key");
    }

    @Test
    void reportsAMissingKeyFile() {
        assertThatThrownBy(() -> TokenKeys.fromPem(new FileSystemResource("missing/signing-key.pem"), VERIFICATION_KEY))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Cannot read token key");
    }

    private static boolean signedAndVerified(TokenKeys keys) throws Exception {
        byte[] message = "header.payload".getBytes(StandardCharsets.US_ASCII);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.signingKey());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(keys.verificationKey());
        verifier.update(message);
        return verifier.verify(signature);
    }
}
