package tech.sarthakgoyal.bastionvault.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;

/**
 * Unseal entry points with brute-force protection and auditing, plus auto-unseal at startup (U-C).
 *
 * <p>Auto-unseal reads the key from VAULT_UNSEAL_KEY_FILE (preferred: a mounted Kubernetes Secret)
 * or VAULT_UNSEAL_KEY. If it is missing or wrong the vault stays sealed and waits for the master
 * password or recovery key.
 */
@Service
public class UnsealService {

    private static final Logger LOG = LoggerFactory.getLogger(UnsealService.class);
    private static final String UNSEAL_KEY = "unseal";

    private final KeyManager keys;
    private final AttemptLimiter limiter;
    private final AuditService audit;
    private final VaultProperties.Unseal config;

    public UnsealService(KeyManager keys, AttemptLimiter limiter, AuditService audit, VaultProperties properties) {
        this.keys = keys;
        this.limiter = limiter;
        this.audit = audit;
        this.config = properties.unseal();
    }

    public boolean withPassword(String admin, String masterPassword) {
        return attempt(admin, "password", () -> keys.unsealWithPassword(masterPassword.toCharArray()));
    }

    public boolean withRecoveryKey(String admin, String recoveryKey) {
        return attempt(admin, "recovery-key", () -> keys.unsealWithRecoveryKey(recoveryKey));
    }

    /** Re-checks the master password for sensitive settings changes, with the same lockout as unseal. */
    public boolean verifyPassword(String admin, String masterPassword) {
        String ipKey = AttemptLimiter.ip(ClientIp.current());
        limiter.assertAllowed(UNSEAL_KEY, ipKey);
        if (keys.verifyPassword(masterPassword.toCharArray())) {
            return true;
        }
        limiter.recordFailure(UNSEAL_KEY, ipKey);
        audit.admin(admin, "VERIFY_MASTER_PASSWORD", null, Outcome.FAILED);
        return false;
    }

    public void seal(String admin) {
        keys.seal();
        audit.admin(admin, "SEAL", null, Outcome.SUCCESS);
    }

    private boolean attempt(String admin, String method, java.util.function.BooleanSupplier unseal) {
        String ipKey = AttemptLimiter.ip(ClientIp.current());
        limiter.assertAllowed(UNSEAL_KEY, ipKey);
        if (!keys.isSealed()) {
            return true;
        }
        if (unseal.getAsBoolean()) {
            limiter.reset(UNSEAL_KEY);
            audit.admin(admin, "UNSEAL", method, Outcome.SUCCESS);
            return true;
        }
        limiter.recordFailure(UNSEAL_KEY, ipKey);
        audit.admin(admin, "UNSEAL", method, Outcome.FAILED);
        return false;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoUnseal() {
        if (!keys.isInitialized() || !keys.isSealed()) {
            return;
        }
        Optional<String> key = configuredUnsealKey();
        if (key.isEmpty()) {
            LOG.info("Vault is sealed; no auto-unseal key configured. Unseal it in the UI.");
            return;
        }
        if (keys.unsealWithUnsealKey(key.get())) {
            LOG.info("Vault auto-unsealed");
            audit.system("UNSEAL", "auto-unseal", Outcome.SUCCESS, null);
        } else {
            LOG.warn("Auto-unseal failed: the configured key does not match (rotated or disabled?). Vault stays sealed.");
            audit.system("UNSEAL", "auto-unseal", Outcome.FAILED, "key mismatch");
        }
    }

    private Optional<String> configuredUnsealKey() {
        if (config.keyFile() != null && !config.keyFile().isBlank()) {
            try {
                return Optional.of(Files.readString(Path.of(config.keyFile()), StandardCharsets.UTF_8).trim());
            } catch (IOException e) {
                LOG.warn("Auto-unseal key file {} is not readable: {}", config.keyFile(), e.getMessage());
                return Optional.empty();
            }
        }
        if (config.key() != null && !config.key().isBlank()) {
            return Optional.of(config.key().trim());
        }
        return Optional.empty();
    }
}
