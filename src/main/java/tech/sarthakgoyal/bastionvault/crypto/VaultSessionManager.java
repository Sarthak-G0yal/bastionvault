package tech.sarthakgoyal.bastionvault.crypto;

import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VaultSessionManager {

    @Value("${vault.salt}")
    private String salt;

    // This is the ONLY place the AES key lives. It is never saved to a database.
    private SecretKey activeKey = null;

    public void unlockVault(String masterPassword) throws Exception {
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
            throw new IllegalStateException("Vault is currently locked. Master Password required.");
        }
        return activeKey;
    }
}
