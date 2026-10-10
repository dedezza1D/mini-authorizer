package com.miniauthorizer.security;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The EC P-256 key pair that signs (ES256) and verifies access tokens.
 */
public record TokenKeys(ECPrivateKey signingKey, ECPublicKey verificationKey) {

    public static TokenKeys fromPem(Resource signingKey, Resource verificationKey) {
        return new TokenKeys(
                (ECPrivateKey) keyFactory(factory -> factory.generatePrivate(new PKCS8EncodedKeySpec(der(signingKey)))),
                (ECPublicKey) keyFactory(factory -> factory.generatePublic(new X509EncodedKeySpec(der(verificationKey)))));
    }

    /**
     * A key pair that lives only in this process: tokens it signs are rejected by any other
     * instance and after a restart. Good enough for one local instance, never for production.
     */
    public static TokenKeys ephemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var pair = generator.generateKeyPair();
            return new TokenKeys((ECPrivateKey) pair.getPrivate(), (ECPublicKey) pair.getPublic());
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("EC P-256 keys are not available", unavailable);
        }
    }

    private static byte[] der(Resource pem) {
        try {
            String body = pem.getContentAsString(StandardCharsets.US_ASCII)
                    .replaceAll("-----(BEGIN|END) [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(body);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read token key " + pem.getDescription(), unreadable);
        }
    }

    private static Object keyFactory(KeyFactoryCall call) {
        try {
            return call.apply(KeyFactory.getInstance("EC"));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Invalid EC P-256 token key", invalid);
        }
    }

    @FunctionalInterface
    private interface KeyFactoryCall {
        Object apply(KeyFactory factory) throws GeneralSecurityException;
    }
}
