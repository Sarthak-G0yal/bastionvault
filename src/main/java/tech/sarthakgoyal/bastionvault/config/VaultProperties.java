package tech.sarthakgoyal.bastionvault.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables under the {@code vault.*} prefix. Defaults live in application.yml.
 *
 * @param kdfIterations PBKDF2-HMAC-SHA256 iterations used for new master-password wraps
 * @param setupToken    optional fixed setup token; when empty a random one is logged at startup
 * @param unseal        auto-unseal key source (U-C): env value or mounted file
 */
@ConfigurationProperties(prefix = "vault")
public record VaultProperties(
        int kdfIterations,
        String setupToken,
        Unseal unseal,
        Token token,
        Lockout lockout,
        Audit audit) {

    public record Unseal(String key, String keyFile) {
    }

    /** Short-lived session tokens issued by POST /api/v1/auth/token. */
    public record Token(int defaultTtlMinutes, int maxTtlMinutes) {
    }

    /** Brute-force protection for admin login, unseal and client authentication. */
    public record Lockout(int maxFailures, int ipMaxFailures, Duration window, Duration lockDuration) {
    }

    public record Audit(int retentionDays) {
    }
}
