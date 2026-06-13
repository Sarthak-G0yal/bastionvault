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
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private final SecureRandom secureRandom = new SecureRandom();

    public String[] encrypt(String plainText, SecretKey secretKey) throws Exception {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] cipherTextBytes = cipher.doFinal(plainText.getBytes());
        String base64CipherText = Base64.getEncoder().encodeToString(cipherTextBytes);
        String base64Iv = Base64.getEncoder().encodeToString(iv);

        return new String[]{base64Iv, base64CipherText};
    }

    public String decrypt(String base64CipherText, String base64Iv, SecretKey secretKey) throws Exception {
        byte[] iv = Base64.getDecoder().decode(base64Iv);
        byte[] cipherTextBytes = Base64.getDecoder().decode(base64CipherText);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] plainTextBytes = cipher.doFinal(cipherTextBytes);
        return new String(plainTextBytes);
    }
}
