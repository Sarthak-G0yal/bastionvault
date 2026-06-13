package tech.sarthakgoyal.bastionvault.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Component;

@Component
public class EncryptionEngine {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private final SecureRandom secureRandom = new SecureRandom();

    // Java 21 Record: Bulletproof data carrier so we never mix up Strings again
    public record CryptoPayload(String ciphertext, String iv) {

    }

    public CryptoPayload encrypt(String plainText, SecretKey secretKey) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] cipherTextBytes = cipher.doFinal(plainText.getBytes());

        String base64Ciphertext = Base64.getEncoder().encodeToString(cipherTextBytes);
        String base64Iv = Base64.getEncoder().encodeToString(iv);

        // Return the strictly named record instead of a blind array
        return new CryptoPayload(base64Ciphertext, base64Iv);
    }

    public String decrypt(String base64Ciphertext, String base64Iv, SecretKey secretKey) throws Exception {
        byte[] cipherTextBytes = Base64.getDecoder().decode(base64Ciphertext);
        byte[] iv = Base64.getDecoder().decode(base64Iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);
        return new String(plainTextBytes);
    }
}
