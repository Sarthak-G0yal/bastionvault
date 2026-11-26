package tech.sarthakgoyal.bastionvault.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM authenticated encryption. Output format: 12-byte nonce || ciphertext || 16-byte tag.
 * The associated data (AAD) binds a ciphertext to its context, so it cannot be moved elsewhere.
 */
public final class Aead {

    public static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Aead() {
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static SecretKey key(byte[] raw) {
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }

    public static byte[] seal(SecretKey key, byte[] plaintext, byte[] aad) {
        try {
            byte[] nonce = randomBytes(NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(NONCE_BYTES + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** @throws DecryptionException if the key is wrong or the data/AAD was tampered with */
    public static byte[] open(SecretKey key, byte[] blob, byte[] aad) {
        if (blob == null || blob.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new DecryptionException("Ciphertext too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(blob, NONCE_BYTES, blob.length - NONCE_BYTES);
        } catch (AEADBadTagException e) {
            throw new DecryptionException("Authentication tag mismatch");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    public static class DecryptionException extends RuntimeException {
        public DecryptionException(String message) {
            super(message);
        }
    }
}
