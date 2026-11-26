package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.crypto.Aead;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.domain.AdminUser;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.repository.AdminUserRepository;

/**
 * First-run initialization. Requires a setup token so that whoever reaches the page first cannot
 * claim the vault: the token comes from VAULT_SETUP_TOKEN or is generated and written to the log.
 */
@Service
public class SetupService {

    private static final Logger LOG = LoggerFactory.getLogger(SetupService.class);

    private final KeyManager keys;
    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AttemptLimiter limiter;
    private final String setupToken;

    public SetupService(KeyManager keys, AdminUserRepository admins, PasswordEncoder passwordEncoder,
            AuditService audit, AttemptLimiter limiter, VaultProperties properties) {
        this.keys = keys;
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.limiter = limiter;
        String configured = properties.setupToken();
        this.setupToken = configured != null && !configured.isBlank()
                ? configured
                : Base64.getUrlEncoder().withoutPadding().encodeToString(Aead.randomBytes(18));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceSetupToken() {
        if (!keys.isInitialized()) {
            LOG.warn("BastionVault is not initialized. Open /setup and enter this setup token: {}", setupToken);
        }
    }

    public boolean isInitialized() {
        return keys.isInitialized();
    }

    /** @return the recovery key, to be shown exactly once */
    @Transactional
    public String initialize(String token, String username, String loginPassword, String masterPassword) {
        String ipKey = AttemptLimiter.ip(ClientIp.current());
        limiter.assertAllowed("setup", ipKey);
        if (isInitialized()) {
            throw new IllegalStateException("Vault is already initialized");
        }
        if (token == null || !MessageDigest.isEqual(token.trim().getBytes(StandardCharsets.UTF_8),
                setupToken.getBytes(StandardCharsets.UTF_8))) {
            limiter.recordFailure("setup", ipKey);
            audit.record(tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType.ANONYMOUS, "anonymous",
                    "SETUP", null, Outcome.DENIED, "bad setup token");
            throw new IllegalArgumentException("Invalid setup token");
        }
        Names.username(username);
        Names.password(loginPassword, "Login password");
        Names.password(masterPassword, "Master password");
        if (loginPassword.equals(masterPassword)) {
            throw new IllegalArgumentException("The master password must differ from the login password");
        }
        admins.save(new AdminUser(username, passwordEncoder.encode(loginPassword)));
        String recoveryKey = keys.initialize(masterPassword.toCharArray());
        audit.admin(username, "SETUP", null, Outcome.SUCCESS);
        return recoveryKey;
    }
}
