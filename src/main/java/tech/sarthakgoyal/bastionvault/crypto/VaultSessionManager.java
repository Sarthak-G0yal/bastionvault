package tech.sarthakgoyal.bastionvault.crypto;

import java.security.spec.KeySpec;

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

    public VaultSessionManager(AdminProfileRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void unlockVault(String masterPassword) throws Exception {
        // 1. Fetch the Admin Profile
        AdminProfile admin = adminRepository.findById("SINGLETON")
                .orElseThrow(() -> new IllegalStateException("Vault not initialized. Please complete setup."));

        // 2. Verify the password matches the BCrypt Hash
        if (!passwordEncoder.matches(masterPassword, admin.getMasterPasswordHash())) {
            throw new IllegalArgumentException("Incorrect Master Password");
        }

        // 3. Math checks out! Derive the AES key and hold it in memory
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterPassword.toCharArray(), salt.getBytes(), 65536, 256);

        byte[] tmp = factory.generateSecret(spec).getEncoded();
        this.activeKey = new SecretKeySpec(tmp, "AES");
    }

    public void lockVault() {
        this.activeKey = null;
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
}
