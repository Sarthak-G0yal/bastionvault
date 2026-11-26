package tech.sarthakgoyal.bastionvault.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.domain.VaultConfig;
import tech.sarthakgoyal.bastionvault.repository.VaultConfigRepository;

/**
 * Owns the seal state (envelope encryption, decision 5B).
 *
 * <p>A random 256-bit data encryption key (DEK) encrypts every secret. The DEK is stored only in
 * wrapped form, once per unlock method:
 * <ul>
 * <li>master password: KEK = PBKDF2-HMAC-SHA256(password, per-install random salt)</li>
 * <li>recovery key: 256 random bits shown once at setup</li>
 * <li>auto-unseal key (optional, U-C): 256 random bits kept in a Kubernetes Secret</li>
 * </ul>
 * Changing the password or rotating a key only re-wraps the DEK; secrets are untouched.
 */
@Component
public class KeyManager {

    public static final int CRYPTO_VERSION = 2;
    private static final byte[] AAD_PASSWORD = aad("password");
    private static final byte[] AAD_RECOVERY = aad("recovery");
    private static final byte[] AAD_UNSEAL = aad("unseal");

    private final VaultConfigRepository configRepository;
    private final VaultProperties properties;
    private final ApplicationEventPublisher events;

    private volatile SecretKey dek;

    public KeyManager(VaultConfigRepository configRepository, VaultProperties properties,
            ApplicationEventPublisher events) {
        this.configRepository = configRepository;
        this.properties = properties;
        this.events = events;
    }

    private static byte[] aad(String method) {
        return ("bastionvault:dek:" + method + ":v2").getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- state

    public boolean isInitialized() {
        return configRepository.existsById(VaultConfig.SINGLETON_ID);
    }

    public boolean isSealed() {
        return dek == null;
    }

    /** @throws VaultSealedException when sealed */
    public SecretKey dek() {
        SecretKey key = dek;
        if (key == null) {
            throw new VaultSealedException();
        }
        return key;
    }

    public void seal() {
        dek = null;
        events.publishEvent(new VaultSealedEvent());
    }

    public boolean hasAutoUnseal() {
        return config().map(c -> c.getDekByUnsealKey() != null).orElse(false);
    }

    // ---------------------------------------------------------------- setup

    /** Creates the DEK and its wraps. Leaves the vault unsealed. Returns the recovery key (show once). */
    @Transactional
    public String initialize(char[] masterPassword) {
        if (isInitialized()) {
            throw new IllegalStateException("Vault is already initialized");
        }
        byte[] rawDek = Aead.randomBytes(Aead.KEY_BYTES);
        byte[] rawRecovery = Aead.randomBytes(Aead.KEY_BYTES);
        try {
            VaultConfig config = new VaultConfig();
            config.setCryptoVersion(CRYPTO_VERSION);
            wrapForPassword(config, rawDek, masterPassword);
            config.setDekByRecovery(Aead.seal(Aead.key(rawRecovery), rawDek, AAD_RECOVERY));
            configRepository.save(config);
            dek = Aead.key(rawDek);
            return KeyCodec.formatRecoveryKey(rawRecovery);
        } finally {
            Arrays.fill(rawDek, (byte) 0);
            Arrays.fill(rawRecovery, (byte) 0);
        }
    }

    // ---------------------------------------------------------------- unseal

    /** @return true when the password was correct and the vault is now unsealed */
    public boolean unsealWithPassword(char[] masterPassword) {
        VaultConfig config = requireConfig();
        SecretKey kek = Kdf.pbkdf2(masterPassword, config.getKdfSalt(), config.getKdfIterations());
        return unwrap(kek, config.getDekByPassword(), AAD_PASSWORD);
    }

    /** Checks the master password without changing the seal state. */
    public boolean verifyPassword(char[] masterPassword) {
        VaultConfig config = requireConfig();
        SecretKey kek = Kdf.pbkdf2(masterPassword, config.getKdfSalt(), config.getKdfIterations());
        try {
            byte[] rawDek = Aead.open(kek, config.getDekByPassword(), AAD_PASSWORD);
            Arrays.fill(rawDek, (byte) 0);
            return true;
        } catch (Aead.DecryptionException e) {
            return false;
        }
    }

    public boolean unsealWithRecoveryKey(String recoveryKey) {
        VaultConfig config = requireConfig();
        byte[] raw;
        try {
            raw = KeyCodec.parseRecoveryKey(recoveryKey);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            return unwrap(Aead.key(raw), config.getDekByRecovery(), AAD_RECOVERY);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    public boolean unsealWithUnsealKey(String unsealKey) {
        VaultConfig config = requireConfig();
        if (config.getDekByUnsealKey() == null) {
            return false;
        }
        byte[] raw;
        try {
            raw = KeyCodec.parseUnsealKey(unsealKey);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            return unwrap(Aead.key(raw), config.getDekByUnsealKey(), AAD_UNSEAL);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private boolean unwrap(SecretKey kek, byte[] wrapped, byte[] aad) {
        byte[] rawDek;
        try {
            rawDek = Aead.open(kek, wrapped, aad);
        } catch (Aead.DecryptionException e) {
            return false;
        }
        try {
            dek = Aead.key(rawDek);
            return true;
        } finally {
            Arrays.fill(rawDek, (byte) 0);
        }
    }

    // ---------------------------------------------------------------- key management (unsealed only)

    @Transactional
    public void changeMasterPassword(char[] newPassword) {
        VaultConfig config = requireConfig();
        byte[] rawDek = dek().getEncoded();
        try {
            wrapForPassword(config, rawDek, newPassword);
            config.touch();
            configRepository.save(config);
        } finally {
            Arrays.fill(rawDek, (byte) 0);
        }
    }

    /** Replaces the recovery key. The old one stops working. Returns the new key (show once). */
    @Transactional
    public String regenerateRecoveryKey() {
        VaultConfig config = requireConfig();
        byte[] rawDek = dek().getEncoded();
        byte[] rawRecovery = Aead.randomBytes(Aead.KEY_BYTES);
        try {
            config.setDekByRecovery(Aead.seal(Aead.key(rawRecovery), rawDek, AAD_RECOVERY));
            config.touch();
            configRepository.save(config);
            return KeyCodec.formatRecoveryKey(rawRecovery);
        } finally {
            Arrays.fill(rawDek, (byte) 0);
            Arrays.fill(rawRecovery, (byte) 0);
        }
    }

    /** Enables (or rotates) auto-unseal. Returns the new unseal key (show once). */
    @Transactional
    public String enableAutoUnseal() {
        VaultConfig config = requireConfig();
        byte[] rawDek = dek().getEncoded();
        byte[] rawUnseal = Aead.randomBytes(Aead.KEY_BYTES);
        try {
            config.setDekByUnsealKey(Aead.seal(Aead.key(rawUnseal), rawDek, AAD_UNSEAL));
            config.touch();
            configRepository.save(config);
            return KeyCodec.formatUnsealKey(rawUnseal);
        } finally {
            Arrays.fill(rawDek, (byte) 0);
            Arrays.fill(rawUnseal, (byte) 0);
        }
    }

    @Transactional
    public void disableAutoUnseal() {
        VaultConfig config = requireConfig();
        dek(); // must be unsealed
        config.setDekByUnsealKey(null);
        config.touch();
        configRepository.save(config);
    }

    // ---------------------------------------------------------------- helpers

    private void wrapForPassword(VaultConfig config, byte[] rawDek, char[] password) {
        byte[] salt = Aead.randomBytes(16);
        int iterations = properties.kdfIterations();
        SecretKey kek = Kdf.pbkdf2(password, salt, iterations);
        config.setKdfAlgorithm(Kdf.PBKDF2_SHA256);
        config.setKdfIterations(iterations);
        config.setKdfSalt(salt);
        config.setDekByPassword(Aead.seal(kek, rawDek, AAD_PASSWORD));
    }

    private Optional<VaultConfig> config() {
        return configRepository.findById(VaultConfig.SINGLETON_ID);
    }

    private VaultConfig requireConfig() {
        return config().orElseThrow(() -> new IllegalStateException("Vault is not initialized"));
    }
}
