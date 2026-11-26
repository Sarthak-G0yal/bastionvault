package tech.sarthakgoyal.bastionvault.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password-based key derivation. */
public final class Kdf {

    public static final String PBKDF2_SHA256 = "PBKDF2WithHmacSHA256";

    private Kdf() {
    }

    public static SecretKey pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, Aead.KEY_BYTES * 8);
        try {
            byte[] raw = SecretKeyFactory.getInstance(PBKDF2_SHA256).generateSecret(spec).getEncoded();
            try {
                return Aead.key(raw.clone());
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key derivation failed", e);
        } finally {
            spec.clearPassword();
        }
    }
}
