package tech.sarthakgoyal.bastionvault.crypto;

import java.security.spec.KeySpec;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import tech.sarthakgoyal.bastionvault.model.AdminProfile;
import tech.sarthakgoyal.bastionvault.repository.AdminProfileRepository;

@Component
public class VaultSessionManager {

    @Value("${vault.salt}")
    private String salt;

    private final AdminProfileRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private SecretKey activeKey = null;

    // Phase 1: Temporary Token Registry for Automation Clients
    private static class TokenMetadata {
        final String clientId;
        final Instant expiresAt;

        TokenMetadata(String clientId, int ttlMinutes) {
            this.clientId = clientId;
            this.expiresAt = Instant.now().plusSeconds(ttlMinutes * 60L);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, TokenMetadata> tokenRegistry = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public VaultSessionManager(AdminProfileRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void unlockVault(String masterPassword) throws Exception {
        AdminProfile admin = adminRepository.findById("SINGLETON")
                .orElseThrow(() -> new IllegalStateException("Vault not initialized. Please complete setup."));

        if (!passwordEncoder.matches(masterPassword, admin.getMasterPasswordHash())) {
            throw new IllegalArgumentException("Incorrect Master Password");
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), salt.getBytes(), 65536, 256);

        byte[] tmp = factory.generateSecret(spec).getEncoded();
        this.activeKey = new SecretKeySpec(tmp, "AES");
    }

    public void lockVault() {
        this.activeKey = null;
        this.tokenRegistry.clear(); // Purge machine tokens upon locking the master engine
    }

    public boolean isUnlocked() {
        return this.activeKey != null;
    }

    public SecretKey getActiveKey() {
        if (activeKey == null) {
            throw new IllegalStateException("Vault is locked. Master Password required.");
        }
        return activeKey;
    }

    // Phase 1 Machine Token Generation Engine
    public String generateTemporaryToken(String clientId, int ttlMinutes) {
        if (!isUnlocked()) {
            throw new IllegalStateException("Cannot issue client tokens while Vault engine is locked.");
        }
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        tokenRegistry.put(token, new TokenMetadata(clientId, ttlMinutes));
        return token;
    }

    public String validateTokenAndGetClientId(String token) {
        TokenMetadata metadata = tokenRegistry.get(token);
        if (metadata == null) {
            return null;
        }
        if (metadata.isExpired()) {
            tokenRegistry.remove(token);
            return null;
        }
        return metadata.clientId;
    }
}