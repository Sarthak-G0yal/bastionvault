package tech.sarthakgoyal.bastionvault.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Reads the pre-0.2 format: AES/CBC/PKCS5Padding with a key of
 * PBKDF2-HMAC-SHA256(masterPassword, VAULT_SALT, 65536). Used only to import old data.
 */
public final class LegacyCbc {

    private static final int LEGACY_ITERATIONS = 65_536;

    private LegacyCbc() {
    }

    public static SecretKey deriveKey(char[] masterPassword, String legacySalt) {
        return Kdf.pbkdf2(masterPassword, legacySalt.getBytes(StandardCharsets.UTF_8), LEGACY_ITERATIONS);
    }

    public static String decrypt(SecretKey key, String ivBase64, String ciphertextBase64) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(Base64.getDecoder().decode(ivBase64)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new Aead.DecryptionException("Legacy value could not be decrypted (wrong password or salt?)");
        }
    }
}
