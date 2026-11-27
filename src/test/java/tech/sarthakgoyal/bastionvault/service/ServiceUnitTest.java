package tech.sarthakgoyal.bastionvault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.crypto.VaultSealedEvent;

class ServiceUnitTest {

    @Test
    void globPatterns() {
        assertThat(GlobMatcher.matches("*", "anything")).isTrue();
        assertThat(GlobMatcher.matches("app-*", "app-firefly")).isTrue();
        assertThat(GlobMatcher.matches("app-*", "other")).isFalse();
        assertThat(GlobMatcher.matches("affine", "affine")).isTrue();
        assertThat(GlobMatcher.matches("affine", "affine2")).isFalse();
        assertThat(GlobMatcher.matches("db?", "db1")).isTrue();
        assertThat(GlobMatcher.matches("a.b", "aXb")).as("dot is literal").isFalse();
        assertThat(GlobMatcher.matches("*-db", "app-db")).isTrue();
        assertThat(GlobMatcher.matches("a*b*c", "aXXbYYc")).isTrue();
        assertThat(GlobMatcher.matches("a*b*c", "aXXbYY")).isFalse();
        assertThat(GlobMatcher.matches("**", "")).isTrue();
        assertThat(GlobMatcher.matches("?", "")).isFalse();
    }

    @Test
    void globMatchingHasNoCatastrophicBacktracking() {
        String pattern = "a*".repeat(60) + "b";   // 121 characters, allowed by Names.groupPattern
        String value = "a".repeat(128);           // the longest valid group name
        long start = System.nanoTime();
        assertThat(GlobMatcher.matches(pattern, value)).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void parallelAttemptsCannotExceedTheLimit() {
        AttemptLimiter limiter = new AttemptLimiter(
                new VaultProperties.Lockout(3, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)), new MutableClock());
        // three slow checks start at once and none has finished yet
        limiter.begin("unseal");
        limiter.begin("unseal");
        limiter.begin("unseal");
        assertThatThrownBy(() -> limiter.begin("unseal")).isInstanceOf(TooManyAttemptsException.class);
        limiter.end(false, "unseal");
        limiter.end(false, "unseal");
        limiter.end(false, "unseal");
        assertThatThrownBy(() -> limiter.begin("unseal")).as("locked after 3 failures")
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void successfulAttemptsDoNotCount() {
        AttemptLimiter limiter = new AttemptLimiter(
                new VaultProperties.Lockout(3, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)), new MutableClock());
        for (int i = 0; i < 10; i++) {
            limiter.begin("admin:bob", "ip:1.2.3.4");
            limiter.end(true, "admin:bob", "ip:1.2.3.4");
        }
        limiter.begin("admin:bob", "ip:1.2.3.4");
    }

    @Test
    void auditFieldsAreBounded() {
        assertThat(AuditService.clip("x".repeat(500), 128)).hasSize(128);
        assertThat(AuditService.clip("a\nb\rc", 128)).isEqualTo("a?b?c");
        assertThat(AuditService.clip(null, 10)).isNull();
    }

    @Test
    void envQuoting() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("PLAIN", "abc def#1");
        values.put("DOLLAR", "pa$$word");
        values.put("QUOTE", "it's");
        values.put("MULTI", "line1\nline2 \"q\" \\ $HOME");
        values.put("private.key", "-----BEGIN-----");
        String env = EnvFormatter.format(values);
        assertThat(env).contains("PLAIN='abc def#1'\n")
                .contains("DOLLAR='pa$$word'\n")
                .contains("QUOTE=\"it's\"\n")
                .contains("MULTI=\"line1\\nline2 \\\"q\\\" \\\\ \\$HOME\"\n")
                .contains("# skipped (not valid variable names): private.key")
                .doesNotContain("BEGIN");
    }

    @Test
    void namesAreValidated() {
        assertThat(Names.group("affine")).isEqualTo("affine");
        assertThatThrownBy(() -> Names.group("../etc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Names.group("a/b")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Names.key("private.key")).isEqualTo("private.key");
        assertThatThrownBy(() -> Names.clientId("Has Upper")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Names.password("short", "pw")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockoutAfterMaxFailuresAndExpiry() {
        MutableClock clock = new MutableClock();
        AttemptLimiter limiter = new AttemptLimiter(
                new VaultProperties.Lockout(3, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock);
        for (int i = 0; i < 2; i++) {
            limiter.recordFailure("admin:bob");
        }
        limiter.assertAllowed("admin:bob");
        limiter.recordFailure("admin:bob");
        assertThatThrownBy(() -> limiter.assertAllowed("admin:bob")).isInstanceOf(TooManyAttemptsException.class);
        limiter.assertAllowed("admin:alice");

        clock.advance(Duration.ofMinutes(16));
        limiter.assertAllowed("admin:bob");
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        MutableClock clock = new MutableClock();
        AttemptLimiter limiter = new AttemptLimiter(
                new VaultProperties.Lockout(3, 10, Duration.ofMinutes(15), Duration.ofMinutes(15)), clock);
        limiter.recordFailure("client:x");
        limiter.recordFailure("client:x");
        clock.advance(Duration.ofMinutes(20));
        limiter.recordFailure("client:x");
        limiter.assertAllowed("client:x");
    }

    @Test
    void tokenLifetimeIsEnforcedByTheServer() {
        TokenService tokens = new TokenService(properties());
        assertThat(tokens.issue("c", null).ttlMinutes()).isEqualTo(15);
        assertThat(tokens.issue("c", 600).ttlMinutes()).as("capped at max").isEqualTo(60);
        assertThatThrownBy(() -> tokens.issue("c", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue("c", -5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokensAreDroppedOnRevokeAndSeal() {
        TokenService tokens = new TokenService(properties());
        String a = tokens.issue("a", 10).token();
        String b = tokens.issue("b", 10).token();
        assertThat(tokens.validate(a)).contains("a");
        tokens.revokeClient("a");
        assertThat(tokens.validate(a)).isEmpty();
        assertThat(tokens.validate(b)).contains("b");
        tokens.onSealed(new VaultSealedEvent());
        assertThat(tokens.validate(b)).isEmpty();
    }

    private static VaultProperties properties() {
        return new VaultProperties(1000, "", new VaultProperties.Unseal("", ""),
                new VaultProperties.Token(15, 60),
                new VaultProperties.Lockout(5, 20, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new VaultProperties.Audit(90));
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
