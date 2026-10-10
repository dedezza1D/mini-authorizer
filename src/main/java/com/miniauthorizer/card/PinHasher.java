package com.miniauthorizer.card;

/**
 * Protects card PINs. The software implementation keeps a secret outside the database; a card
 * network would implement it with an HSM, so that the PIN is never seen in clear text.
 */
public interface PinHasher {

    String hash(String cardNumber, String pin);

    boolean matches(String cardNumber, String pin, String storedHash);
}
