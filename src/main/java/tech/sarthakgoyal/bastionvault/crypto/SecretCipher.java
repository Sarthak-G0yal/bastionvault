package tech.sarthakgoyal.bastionvault.crypto;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

/** Encrypts secret values with the DEK, binding each ciphertext to its (group, key) address. */
public final class SecretCipher {

    /** Version 1 was the legacy AES-CBC format (Mongo era); it only exists in imports. */
    public static final int ENC_VERSION_GCM = 2;

    private SecretCipher() {
    }

    public static byte[] encrypt(SecretKey dek, String group, String key, String value) {
        return Aead.seal(dek, value.getBytes(StandardCharsets.UTF_8), aad(group, key));
    }

    public static String decrypt(SecretKey dek, String group, String key, byte[] blob) {
        return new String(Aead.open(dek, blob, aad(group, key)), StandardCharsets.UTF_8);
    }

    private static byte[] aad(String group, String key) {
        return ("bastionvault:secret:v2\0" + group + "\0" + key).getBytes(StandardCharsets.UTF_8);
    }
}
