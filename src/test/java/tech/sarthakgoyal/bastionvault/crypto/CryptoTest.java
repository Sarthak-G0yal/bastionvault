package tech.sarthakgoyal.bastionvault.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.junit.jupiter.api.Test;

class CryptoTest {

    private final SecretKey key = Aead.key(Aead.randomBytes(32));
    private final byte[] aad = "ctx".getBytes(StandardCharsets.UTF_8);

    @Test
    void gcmRoundTrip() {
        byte[] blob = Aead.seal(key, "hello".getBytes(StandardCharsets.UTF_8), aad);
        assertThat(new String(Aead.open(key, blob, aad), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void sameInputGivesDifferentCiphertexts() {
        byte[] a = Aead.seal(key, "x".getBytes(StandardCharsets.UTF_8), aad);
        byte[] b = Aead.seal(key, "x".getBytes(StandardCharsets.UTF_8), aad);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] blob = Aead.seal(key, "hello".getBytes(StandardCharsets.UTF_8), aad);
        blob[blob.length - 1] ^= 1;
        assertThatThrownBy(() -> Aead.open(key, blob, aad)).isInstanceOf(Aead.DecryptionException.class);
    }

    @Test
    void wrongKeyOrAadIsRejected() {
        byte[] blob = Aead.seal(key, "hello".getBytes(StandardCharsets.UTF_8), aad);
        assertThatThrownBy(() -> Aead.open(Aead.key(Aead.randomBytes(32)), blob, aad))
                .isInstanceOf(Aead.DecryptionException.class);
        assertThatThrownBy(() -> Aead.open(key, blob, "other".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(Aead.DecryptionException.class);
    }

    @Test
    void secretCannotBeMovedToAnotherAddress() {
        byte[] blob = SecretCipher.encrypt(key, "app", "DB_PASS", "s3cret ✓");
        assertThat(SecretCipher.decrypt(key, "app", "DB_PASS", blob)).isEqualTo("s3cret ✓");
        assertThatThrownBy(() -> SecretCipher.decrypt(key, "other", "DB_PASS", blob))
                .isInstanceOf(Aead.DecryptionException.class);
        assertThatThrownBy(() -> SecretCipher.decrypt(key, "app", "API_KEY", blob))
                .isInstanceOf(Aead.DecryptionException.class);
    }

    @Test
    void recoveryKeyFormatsAndParsesLeniently() {
        byte[] raw = Aead.randomBytes(32);
        String text = KeyCodec.formatRecoveryKey(raw);
        assertThat(text).matches("([0-9a-f]{8}-){7}[0-9a-f]{8}");
        assertThat(KeyCodec.parseRecoveryKey(" " + text.toUpperCase().replace("-", " ") + "\n")).isEqualTo(raw);
        assertThatThrownBy(() -> KeyCodec.parseRecoveryKey("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsealKeyRoundTrip() {
        byte[] raw = Aead.randomBytes(32);
        String text = KeyCodec.formatUnsealKey(raw);
        assertThat(text).startsWith("bvu_");
        assertThat(KeyCodec.parseUnsealKey(text + "\n")).isEqualTo(raw);
        assertThatThrownBy(() -> KeyCodec.parseUnsealKey("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Encrypts exactly like BastionVault 0.1 did, then checks the importer can read it. */
    @Test
    void legacyCbcValuesCanBeRead() throws Exception {
        String password = "old-master-password";
        String salt = "legacy-test-salt";
        SecretKey legacyKey = LegacyCbc.deriveKey(password.toCharArray(), salt);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new IvParameterSpec(iv));
        String ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal("legacy-value".getBytes()));
        String ivText = Base64.getEncoder().encodeToString(iv);

        assertThat(LegacyCbc.decrypt(legacyKey, ivText, ciphertext)).isEqualTo("legacy-value");
        SecretKey wrong = LegacyCbc.deriveKey("wrong".toCharArray(), salt);
        assertThatThrownBy(() -> LegacyCbc.decrypt(wrong, ivText, ciphertext))
                .isInstanceOf(Aead.DecryptionException.class);
    }
}
